package com.gucardev.dynamicreportsentity.reporting.web;

import com.gucardev.dynamicreportsentity.reporting.ReportViewer;
import com.gucardev.dynamicreportsentity.reporting.catalog.FieldType;
import com.gucardev.dynamicreportsentity.reporting.catalog.ReportCatalog;
import com.gucardev.dynamicreportsentity.reporting.query.Aggregate;
import com.gucardev.dynamicreportsentity.reporting.query.DateGroup;
import com.gucardev.dynamicreportsentity.reporting.query.FilterOperator;
import com.gucardev.dynamicreportsentity.reporting.query.ReportDefinition;
import com.gucardev.dynamicreportsentity.reporting.query.ReportExecutor;
import com.gucardev.dynamicreportsentity.reporting.query.ReportQuery;
import com.gucardev.dynamicreportsentity.reporting.query.ReportResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Endpoints used by the business users' report builder.
 * <p>
 * {@code @PreAuthorize} answers "may this user use reporting at all?". Which fields and rows they
 * may see is dynamic, so that is enforced by the catalog / query builder instead.
 */
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('REPORT_RUN')")
class ReportController {

    private final ReportCatalog catalog;
    private final ReportExecutor executor;

    /**
     * When the screen opens: every entity with the fields this user may use and the relations to
     * navigate. The UI builds paths from it (root → relation → … → field). No tables/columns exposed.
     */
    @GetMapping("/catalog")
    public CatalogResponse catalog(Authentication authentication) {
        ReportViewer viewer = viewer(authentication);
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

    /** "Run" on screen: always paged. */
    @PostMapping("/run")
    public ReportResult run(@Valid @RequestBody ReportDefinition definition,
                            @RequestParam(defaultValue = "0") int page,
                            @RequestParam(required = false) Integer size,
                            Authentication authentication) {
        return executor.run(definition, viewer(authentication), page, size);
    }

    /**
     * "Download" on screen: CSV streamed row by row instead of one big JSON array.
     * The query is built (and permissions checked) before streaming starts, so errors still map to 4xx.
     */
    @PostMapping("/export")
    public ResponseEntity<StreamingResponseBody> export(@Valid @RequestBody ReportDefinition definition,
                                                        Authentication authentication) {
        ReportQuery query = executor.prepare(definition, viewer(authentication));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("report.csv").build().toString())
                .contentType(new MediaType("text", "csv", UTF_8))
                .body(outputStream -> executor.writeCsv(query, outputStream));
    }

    /** Shows the generated SQL without executing it. Reveals schema details, so admins only. */
    @PostMapping("/preview")
    @PreAuthorize("hasAuthority('REPORT_ADMIN')")
    public SqlPreview preview(@Valid @RequestBody ReportDefinition definition, Authentication authentication) {
        ReportQuery query = executor.prepare(definition, viewer(authentication));
        return new SqlPreview(query.sql(), query.parameters());
    }

    private static ReportViewer viewer(Authentication authentication) {
        return new ReportViewer(authentication.getName(), authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));
    }

    record CatalogResponse(List<Entity> entities, List<FilterOperator> operators, List<Aggregate> aggregates,
                           List<DateGroup> dateGroups) {

        record Entity(String name, String label, List<Field> fields, List<Relation> relations) {
        }

        record Field(String name, String label, FieldType type) {
        }

        /** {@code toMany}: following it can return several rows per row (one-to-many / many-to-many). */
        record Relation(String name, String label, String target, boolean toMany) {
        }
    }

    record SqlPreview(String sql, List<Object> parameters) {
    }
}
