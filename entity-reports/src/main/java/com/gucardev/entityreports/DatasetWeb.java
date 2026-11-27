package com.gucardev.entityreports;

import com.gucardev.entityreports.Dataset.AccessDeniedException;
import com.gucardev.entityreports.Dataset.Aggregate;
import com.gucardev.entityreports.Dataset.DateGroup;
import com.gucardev.entityreports.Dataset.Definition;
import com.gucardev.entityreports.Dataset.ExportFormat;
import com.gucardev.entityreports.Dataset.FieldType;
import com.gucardev.entityreports.Dataset.FilterOperator;
import com.gucardev.entityreports.Dataset.InvalidException;
import com.gucardev.entityreports.Dataset.Query;
import com.gucardev.entityreports.Dataset.Result;
import com.gucardev.entityreports.Dataset.Viewer;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.net.URI;
import java.security.Principal;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * The REST endpoints (registered by the auto-configuration, paths from {@code entity-reports.web.*}):
 * <ul>
 *   <li>{@code GET  {path}/catalog} – entities, fields and relations the caller may use</li>
 *   <li>{@code POST {path}/run?page&size&includeTotal} – one page of rows</li>
 *   <li>{@code POST {path}/export?format=xlsx|csv} – the whole dataset as a file, streamed</li>
 *   <li>{@code POST {path}/preview} – generated SQL and index hints (admins only)</li>
 *   <li>{@code GET  {path}/index-advice} – unindexed columns people actually filtered/joined on</li>
 * </ul>
 * Errors are RFC 9457 problems with a stable {@code code}. Saved datasets have their own endpoints, in
 * {@link SavedDatasets} (leave that file out if you do not need them).
 */
public final class DatasetWeb {

    private DatasetWeb() {
    }

    /**
     * Who is calling. The default reads the servlet principal: with Spring Security that is the
     * {@code Authentication}, whose authorities are read too (by reflection, so this code compiles without Spring
     * Security on the classpath). Define your own bean to read a token, a header, your user table...
     */
    @FunctionalInterface
    public interface ViewerResolver {
        Viewer resolve(HttpServletRequest request);

        static ViewerResolver principal() {
            return request -> {
                Principal principal = request.getUserPrincipal();
                return principal == null ? Viewer.of("anonymous", Set.of())
                        : Viewer.of(principal.getName(), authorities(principal));
            };
        }

        /** {@code Authentication.getAuthorities()[*].getAuthority()} when the principal has them, else none. */
        private static List<String> authorities(Principal principal) {
            try {
                Object authorities = principal.getClass().getMethod("getAuthorities").invoke(principal);
                if (!(authorities instanceof Collection<?> collection)) {
                    return List.of();
                }
                List<String> names = new ArrayList<>();
                for (Object authority : collection) {
                    Object name = authority.getClass().getMethod("getAuthority").invoke(authority);
                    if (name != null) {
                        names.add(name.toString());
                    }
                }
                return names;
            } catch (ReflectiveOperationException | RuntimeException e) {
                return List.of();
            }
        }
    }

    /** Shared by both controllers: the caller, refused when it lacks {@code entity-reports.web.user-authority}. */
    static Viewer caller(ViewerResolver resolver, EntityReportsProperties properties, HttpServletRequest request) {
        Viewer viewer = resolver.resolve(request);
        String required = properties.web().userAuthority();
        if (!required.isBlank() && !viewer.unrestricted() && !viewer.hasAuthority(required)) {
            throw new AccessDeniedException("You are not allowed to use datasets");
        }
        return viewer;
    }

    // ==== datasets ========================================================================================

    @RestController
    @RequestMapping("${entity-reports.web.path:/api/datasets}")
    public static class DatasetController {

        private final DatasetCatalog catalog;
        private final DatasetExecutor executor;
        private final IndexAdvisor indexAdvisor;
        private final ViewerResolver viewers;
        private final EntityReportsProperties properties;

        public DatasetController(DatasetCatalog catalog, DatasetExecutor executor, IndexAdvisor indexAdvisor,
                                 ViewerResolver viewers, EntityReportsProperties properties) {
            this.catalog = catalog;
            this.executor = executor;
            this.indexAdvisor = indexAdvisor;
            this.viewers = viewers;
            this.properties = properties;
        }

        /**
         * When the builder opens: every entity with the fields this caller may use and the relations to navigate.
         * The UI builds paths from it (root → relation → … → field). No tables or columns are exposed.
         */
        @GetMapping("/catalog")
        public CatalogResponse catalog(HttpServletRequest request) {
            Viewer viewer = caller(viewers, properties, request);
            List<CatalogResponse.Entity> entities = catalog.entities().stream()
                    .map(entity -> new CatalogResponse.Entity(entity.name(), entity.label(),
                            entity.fields().values().stream()
                                    .filter(field -> field.isVisibleTo(viewer))
                                    .map(field -> new CatalogResponse.Field(field.name(), field.label(), field.type()))
                                    .toList(),
                            entity.navigations().values().stream()
                                    .map(navigation -> new CatalogResponse.Relation(navigation.name(), navigation.label(),
                                            navigation.targetEntity(), navigation.toMany()))
                                    .toList()))
                    .toList();
            return new CatalogResponse(entities, List.of(FilterOperator.values()), List.of(Aggregate.values()),
                    List.of(DateGroup.values()));
        }

        /**
         * Always paged. The total is off by default: on big data its {@code COUNT(*)} costs more than the page
         * itself; {@code includeTotal=true} turns it on.
         */
        @PostMapping("/run")
        public Result run(@Valid @RequestBody Definition definition,
                          @RequestParam(defaultValue = "0") int page,
                          @RequestParam(required = false) Integer size,
                          @RequestParam(defaultValue = "false") boolean includeTotal,
                          HttpServletRequest request) {
            return executor.run(definition, caller(viewers, properties, request), page, size, includeTotal);
        }

        /**
         * The file streamed row by row. The query is built (and permissions checked) before streaming starts, so
         * errors still map to 4xx.
         */
        @PostMapping("/export")
        public ResponseEntity<StreamingResponseBody> export(@Valid @RequestBody Definition definition,
                                                            @RequestParam(defaultValue = "csv") String format,
                                                            HttpServletRequest request) {
            ExportFormat fileFormat;
            try {
                fileFormat = ExportFormat.of(format);
            } catch (IllegalArgumentException e) {
                throw new InvalidException("format must be xlsx or csv");
            }
            Query query = executor.prepare(definition, caller(viewers, properties, request));
            MediaType type = fileFormat == ExportFormat.CSV ? new MediaType("text", "csv", UTF_8)
                    : MediaType.parseMediaType(fileFormat.contentType());
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                            .filename("dataset" + fileFormat.extension()).build().toString())
                    .contentType(type)
                    .body(outputStream -> executor.write(query, fileFormat, outputStream));
        }

        /** The generated SQL without executing it, plus index hints. Reveals the schema, so admins only. */
        @PostMapping("/preview")
        public SqlPreview preview(@Valid @RequestBody Definition definition, HttpServletRequest request) {
            Viewer viewer = caller(viewers, properties, request);
            String admin = properties.adminAuthority();
            if (!viewer.unrestricted() && (admin.isBlank() || !viewer.hasAuthority(admin))) {
                throw new AccessDeniedException("Only admins may see the generated SQL");
            }
            Query query = executor.prepare(definition, viewer);
            return new SqlPreview(query.sql(), query.parameters(), indexAdvisor.hints(query.columnUsages()));
        }

        /**
         * Index advice collected from the datasets people actually ran: unindexed columns, how often and how slowly
         * they were used, and ready-to-review DDL. Nothing is created automatically.
         */
        @GetMapping("/index-advice")
        public List<IndexAdvisor.Advice> indexAdvice(HttpServletRequest request) throws SQLException {
            caller(viewers, properties, request);
            return indexAdvisor.advice();
        }

        public record CatalogResponse(List<Entity> entities, List<FilterOperator> operators,
                                      List<Aggregate> aggregates, List<DateGroup> dateGroups) {

            public record Entity(String name, String label, List<Field> fields, List<Relation> relations) {
            }

            public record Field(String name, String label, FieldType type) {
            }

            /** {@code toMany}: following it can return several rows per row (one-to-many / many-to-many). */
            public record Relation(String name, String label, String target, boolean toMany) {
            }
        }

        public record SqlPreview(String sql, List<Object> parameters, List<String> indexHints) {
        }
    }

    // ==== errors ==========================================================================================

    /**
     * Problems of {@link DatasetController}: {@code type}, {@code title}, {@code detail}, {@code instance}, a stable
     * {@code code} and a {@code traceId} (the {@code X-Request-Id} header when the client sent one).
     */
    @RestControllerAdvice(assignableTypes = DatasetController.class)
    public static class Errors {

        @ExceptionHandler(InvalidException.class)
        public ProblemDetail invalid(InvalidException exception, HttpServletRequest request) {
            return problem(HttpStatus.BAD_REQUEST, "INVALID_DATASET_DEFINITION", "Invalid dataset definition",
                    exception.getMessage(), request);
        }

        @ExceptionHandler(AccessDeniedException.class)
        public ProblemDetail denied(AccessDeniedException exception, HttpServletRequest request) {
            return problem(HttpStatus.FORBIDDEN, "DATASET_ACCESS_DENIED", "Access denied", exception.getMessage(),
                    request);
        }

        @ExceptionHandler(MethodArgumentNotValidException.class)
        public ProblemDetail validation(MethodArgumentNotValidException exception, HttpServletRequest request) {
            return validationProblem(HttpStatus.BAD_REQUEST, exception, request);
        }

        @ExceptionHandler(HttpMessageNotReadableException.class)
        public ProblemDetail unreadable(HttpMessageNotReadableException exception, HttpServletRequest request) {
            return problem(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", null,
                    "The request body is not valid JSON or a value has the wrong type.", request);
        }

        @ExceptionHandler(MethodArgumentTypeMismatchException.class)
        public ProblemDetail typeMismatch(MethodArgumentTypeMismatchException exception, HttpServletRequest request) {
            if (exception.getRequiredType() == UUID.class) {
                // ids are UUIDs: anything else in the path is simply a record that does not exist
                return problem(HttpStatus.NOT_FOUND, "NOT_FOUND", null, "There is no such record.", request);
            }
            return problem(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER", null,
                    "Invalid value for '" + exception.getName() + "'.", request);
        }

        public record FieldError(String field, String code, String message) {
        }

        static ProblemDetail validationProblem(HttpStatus status, MethodArgumentNotValidException exception,
                                               HttpServletRequest request) {
            List<FieldError> errors = exception.getBindingResult().getFieldErrors().stream()
                    .map(error -> new FieldError(error.getField(), error.getCode(), error.getDefaultMessage()))
                    .toList();
            ProblemDetail problem = problem(status, "VALIDATION_FAILED", "Validation failed",
                    errors.size() == 1 ? "1 field is invalid." : errors.size() + " fields are invalid.", request);
            problem.setProperty("errors", errors);
            return problem;
        }

        static ProblemDetail problem(HttpStatus status, String code, String title, String detail,
                                             HttpServletRequest request) {
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
            problem.setType(URI.create("/problems/" + code.toLowerCase(Locale.ROOT).replace('_', '-')));
            if (title != null) {
                problem.setTitle(title);
            }
            problem.setInstance(URI.create(request.getRequestURI()));
            problem.setProperty("code", code);
            String requestId = request.getHeader("X-Request-Id");
            problem.setProperty("traceId", requestId != null && requestId.matches("[\\w-]{1,64}") ? requestId
                    : UUID.randomUUID().toString().replace("-", "").substring(0, 16));
            return problem;
        }
    }
}
