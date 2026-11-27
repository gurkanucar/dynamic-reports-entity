package com.gucardev.entityreports;

import com.gucardev.entityreports.Dataset.AccessDeniedException;
import com.gucardev.entityreports.Dataset.Definition;
import com.gucardev.entityreports.Dataset.InvalidException;
import com.gucardev.entityreports.Dataset.Viewer;
import com.gucardev.entityreports.DatasetWeb.ViewerResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import tools.jackson.databind.json.JsonMapper;

import javax.sql.DataSource;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Saved datasets: a definition stored under a unique name, which other features (scheduled e-mails, dashboards...)
 * use like a variable. Nothing but the definition is stored: whoever runs it gets the rows their permissions allow.
 * Anyone may save one (the definition must be valid for them); only its creator or an admin
 * ({@code entity-reports.admin-authority}) may change or delete it; one in {@link Usage use} cannot be deleted.
 * <p>
 * Plain JDBC on table {@code saved_dataset} (DDL: {@code META-INF/entity-reports/schema.sql}), so the library never
 * touches the host's JPA entity or repository scanning.
 * <p>
 * <b>Optional and self-contained:</b> this file holds the service, its REST endpoints, their errors and their Spring
 * setup. Nothing else refers to it, so a project that does not want saved datasets simply leaves it out.
 */
public class SavedDatasets {

    private static final Pattern NAME = Pattern.compile("[a-z][a-z0-9_]{1,39}");
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String COLUMNS = "id, name, label, description, definition_json, version, created_by,"
            + " created_at, updated_by, updated_at";

    /** A saved dataset as callers and the API see it. */
    public record Info(UUID id, String name, String label, String description, Definition definition, long version,
                       String createdBy, Instant createdAt, String updatedBy, Instant updatedAt) {
    }

    /**
     * Creates or replaces a saved dataset.
     *
     * @param name lower case letters, digits and underscores ({@code department_headcount}); ignored on update
     */
    public record Request(String name, @Size(max = 255) String label, @Size(max = 1000) String description,
                          @NotNull @Valid Definition definition) {
    }

    /** Implemented by features that use saved datasets by name, so one in use cannot be deleted. */
    @FunctionalInterface
    public interface Usage {
        boolean usesSavedDataset(String name);
    }

    /** A request that cannot be served; {@code code} is stable and machine-readable. */
    public static class Problem extends RuntimeException {

        public enum Kind { NOT_FOUND, CONFLICT, FORBIDDEN }

        private final Kind kind;
        private final String code;

        Problem(Kind kind, String code, String message) {
            super(message);
            this.kind = kind;
            this.code = code;
        }

        public Kind kind() {
            return kind;
        }

        public String code() {
            return code;
        }
    }

    private final JdbcTemplate jdbc;
    private final DatasetExecutor executor;
    private final Clock clock;
    private final String adminAuthority;
    private final List<Usage> usages;

    public SavedDatasets(JdbcTemplate jdbc, DatasetExecutor executor, Clock clock, String adminAuthority,
                         List<Usage> usages) {
        this.jdbc = jdbc;
        this.executor = executor;
        this.clock = clock;
        this.adminAuthority = adminAuthority;
        this.usages = List.copyOf(usages);
    }

    public List<Info> list() {
        return jdbc.query("select " + COLUMNS + " from saved_dataset order by name", ROW);
    }

    public Info get(UUID id) {
        return load(id);
    }

    /** By name: how other features refer to a dataset. */
    public Optional<Info> find(String name) {
        return jdbc.query("select " + COLUMNS + " from saved_dataset where name = ?", ROW, name).stream().findFirst();
    }

    public Info create(Request request, Viewer editor) {
        String name = request.name();
        if (name == null || !NAME.matcher(name).matches()) {
            throw new InvalidException("The name must be 2-40 lower case letters, digits or underscores, "
                    + "starting with a letter (e.g. department_headcount)");
        }
        executor.prepare(request.definition(), editor);
        UUID id = uuidV7();
        OffsetDateTime now = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        try {
            jdbc.update("insert into saved_dataset (" + COLUMNS + ") values (?, ?, ?, ?, ?, 0, ?, ?, ?, ?)",
                    id, name, request.label(), request.description(), JSON.writeValueAsString(request.definition()),
                    editor.username(), now, editor.username(), now);
        } catch (DuplicateKeyException e) {
            throw new Problem(Problem.Kind.CONFLICT, "SAVED_DATASET_NAME_TAKEN", "Dataset '" + name + "' already exists");
        }
        return load(id);
    }

    public Info update(UUID id, Request request, Viewer editor) {
        Info current = load(id);
        requireOwner(current, editor);
        executor.prepare(request.definition(), editor);
        int updated = jdbc.update("update saved_dataset set label = ?, description = ?, definition_json = ?,"
                        + " updated_by = ?, updated_at = ?, version = version + 1 where id = ? and version = ?",
                request.label(), request.description(), JSON.writeValueAsString(request.definition()),
                editor.username(), OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC), id, current.version());
        if (updated == 0) {
            throw new Problem(Problem.Kind.CONFLICT, "VERSION_MISMATCH",
                    "Dataset '" + current.name() + "' was changed by someone else; reload it and try again");
        }
        return load(id);
    }

    /** Not while something uses it (e.g. a scheduled job). */
    public void delete(UUID id, Viewer editor) {
        Info current = load(id);
        requireOwner(current, editor);
        if (usages.stream().anyMatch(usage -> usage.usesSavedDataset(current.name()))) {
            throw new Problem(Problem.Kind.CONFLICT, "SAVED_DATASET_IN_USE",
                    "Dataset '" + current.name() + "' is in use; change what uses it first");
        }
        jdbc.update("delete from saved_dataset where id = ?", id);
    }

    private void requireOwner(Info dataset, Viewer editor) {
        boolean admin = editor.unrestricted() || (!adminAuthority.isBlank() && editor.hasAuthority(adminAuthority));
        if (!admin && !editor.username().equals(dataset.createdBy())) {
            throw new Problem(Problem.Kind.FORBIDDEN, "NOT_DATASET_OWNER", "Only " + dataset.createdBy()
                    + " or an admin may change dataset '" + dataset.name() + "'");
        }
    }

    private Info load(UUID id) {
        return jdbc.query("select " + COLUMNS + " from saved_dataset where id = ?", ROW, id).stream().findFirst()
                .orElseThrow(() -> new Problem(Problem.Kind.NOT_FOUND, "SAVED_DATASET_NOT_FOUND",
                        "There is no dataset " + id));
    }

    private static final RowMapper<Info> ROW = (rs, rowNumber) -> new Info(
            rs.getObject("id", UUID.class), rs.getString("name"), rs.getString("label"), rs.getString("description"),
            JSON.readValue(rs.getString("definition_json"), Definition.class), rs.getLong("version"),
            rs.getString("created_by"), rs.getObject("created_at", OffsetDateTime.class).toInstant(),
            rs.getString("updated_by"), rs.getObject("updated_at", OffsetDateTime.class).toInstant());

    /** Time-ordered and not guessable (RFC 9562 version 7): ids end up in URLs. */
    static UUID uuidV7() {
        long millis = System.currentTimeMillis();
        long high = (millis << 16) | 0x7000L | (RANDOM.nextInt() & 0x0FFF);
        long low = (RANDOM.nextLong() & 0x3FFFFFFFFFFFFFFFL) | 0x8000000000000000L;
        return new UUID(high, low);
    }

    // ==== REST: {entity-reports.web.saved-path}, answers as {"data": ...} ==================================

    /** Success envelope. */
    public record Data<T>(T data) {
    }

    @RestController
    @RequestMapping("${entity-reports.web.saved-path:/api/saved-datasets}")
    public static class Controller {

        private final SavedDatasets datasets;
        private final ViewerResolver viewers;
        private final EntityReportsProperties properties;

        public Controller(SavedDatasets datasets, ViewerResolver viewers, EntityReportsProperties properties) {
            this.datasets = datasets;
            this.viewers = viewers;
            this.properties = properties;
        }

        @GetMapping
        public Data<List<Info>> list(HttpServletRequest request) {
            DatasetWeb.caller(viewers, properties, request);
            return new Data<>(datasets.list());
        }

        @GetMapping("/{id}")
        public Data<Info> get(@PathVariable UUID id, HttpServletRequest request) {
            DatasetWeb.caller(viewers, properties, request);
            return new Data<>(datasets.get(id));
        }

        @PostMapping
        @ResponseStatus(HttpStatus.CREATED)
        public Data<Info> create(@Valid @RequestBody Request body, HttpServletRequest request) {
            return new Data<>(datasets.create(body, DatasetWeb.caller(viewers, properties, request)));
        }

        @PutMapping("/{id}")
        public Data<Info> update(@PathVariable UUID id, @Valid @RequestBody Request body, HttpServletRequest request) {
            return new Data<>(datasets.update(id, body, DatasetWeb.caller(viewers, properties, request)));
        }

        @DeleteMapping("/{id}")
        @ResponseStatus(HttpStatus.NO_CONTENT)
        public void delete(@PathVariable UUID id, HttpServletRequest request) {
            datasets.delete(id, DatasetWeb.caller(viewers, properties, request));
        }
    }

    /** RFC 9457 problems with a stable {@code code}; an invalid definition is 422 here (400 when running it). */
    @RestControllerAdvice(assignableTypes = Controller.class)
    public static class Errors {

        private static final HttpStatus UNPROCESSABLE = HttpStatus.valueOf(422);

        @ExceptionHandler(Problem.class)
        public ProblemDetail problem(Problem exception, HttpServletRequest request) {
            HttpStatus status = switch (exception.kind()) {
                case NOT_FOUND -> HttpStatus.NOT_FOUND;
                case CONFLICT -> HttpStatus.CONFLICT;
                case FORBIDDEN -> HttpStatus.FORBIDDEN;
            };
            return DatasetWeb.Errors.problem(status, exception.code(), null, exception.getMessage(), request);
        }

        @ExceptionHandler(InvalidException.class)
        public ProblemDetail invalid(InvalidException exception, HttpServletRequest request) {
            return DatasetWeb.Errors.problem(UNPROCESSABLE, "INVALID_DATASET_DEFINITION", "Invalid dataset definition",
                    exception.getMessage(), request);
        }

        @ExceptionHandler(AccessDeniedException.class)
        public ProblemDetail denied(AccessDeniedException exception, HttpServletRequest request) {
            return DatasetWeb.Errors.problem(HttpStatus.FORBIDDEN, "DATASET_ACCESS_DENIED", "Access denied",
                    exception.getMessage(), request);
        }

        @ExceptionHandler(MethodArgumentNotValidException.class)
        public ProblemDetail validation(MethodArgumentNotValidException exception, HttpServletRequest request) {
            return DatasetWeb.Errors.validationProblem(UNPROCESSABLE, exception, request);
        }

        @ExceptionHandler(HttpMessageNotReadableException.class)
        public ProblemDetail unreadable(HttpMessageNotReadableException exception, HttpServletRequest request) {
            return DatasetWeb.Errors.problem(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", null,
                    "The request body is not valid JSON or a value has the wrong type.", request);
        }

        @ExceptionHandler(MethodArgumentTypeMismatchException.class)
        public ProblemDetail typeMismatch(MethodArgumentTypeMismatchException exception, HttpServletRequest request) {
            return DatasetWeb.Errors.problem(HttpStatus.NOT_FOUND, "NOT_FOUND", null, "There is no such record.",
                    request);
        }
    }

    // ==== Spring setup ====================================================================================

    /**
     * Registered after {@link EntityReportsAutoConfiguration} (library jar), or picked up by component scanning when
     * the sources are copied into a project. Off with {@code entity-reports.saved.enabled=false}.
     */
    @Configuration(proxyBeanMethods = false)   // not @AutoConfiguration: see EntityReportsAutoConfiguration
    @AutoConfigureAfter(EntityReportsAutoConfiguration.class)
    @ConditionalOnProperty(prefix = "entity-reports.saved", name = "enabled", matchIfMissing = true)
    public static class Setup {

        @Bean
        @ConditionalOnMissingBean
        SavedDatasets savedDatasets(DataSource dataSource, ObjectProvider<JdbcTemplate> jdbcTemplate,
                                    DatasetExecutor executor, ObjectProvider<Clock> clock,
                                    ObjectProvider<Usage> usages, EntityReportsProperties properties) {
            if (properties.saved().initializeSchema()) {
                new ResourceDatabasePopulator(new ClassPathResource("META-INF/entity-reports/schema.sql"))
                        .execute(dataSource);
            }
            return new SavedDatasets(jdbcTemplate.getIfAvailable(() -> new JdbcTemplate(dataSource)), executor,
                    clock.getIfAvailable(Clock::systemDefaultZone), properties.adminAuthority(),
                    usages.orderedStream().toList());
        }

        @Configuration(proxyBeanMethods = false)
        @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
        @ConditionalOnClass(RestController.class)
        @ConditionalOnProperty(prefix = "entity-reports.web", name = "enabled", matchIfMissing = true)
        static class Web {

            @Bean
            @ConditionalOnMissingBean
            Controller savedDatasetController(SavedDatasets datasets, ViewerResolver viewers,
                                              EntityReportsProperties properties) {
                return new Controller(datasets, viewers, properties);
            }

            @Bean
            @ConditionalOnMissingBean
            Errors savedDatasetErrors() {
                return new Errors();
            }
        }
    }
}
