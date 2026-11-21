package com.gucardev.dynamicreportsentity.platform.dataset.web;

import com.gucardev.dynamicreportsentity.platform.identity.Actor;
import com.gucardev.dynamicreportsentity.platform.dataset.catalog.FieldType;
import com.gucardev.dynamicreportsentity.platform.dataset.catalog.IndexAdvisor;
import com.gucardev.dynamicreportsentity.platform.dataset.catalog.DatasetCatalog;
import com.gucardev.dynamicreportsentity.platform.dataset.query.Aggregate;
import com.gucardev.dynamicreportsentity.platform.dataset.query.DateGroup;
import com.gucardev.dynamicreportsentity.platform.dataset.query.FilterOperator;
import com.gucardev.dynamicreportsentity.platform.dataset.query.DatasetDefinition;
import com.gucardev.dynamicreportsentity.platform.dataset.query.DatasetExecutor;
import com.gucardev.dynamicreportsentity.platform.dataset.query.DatasetQuery;
import com.gucardev.dynamicreportsentity.platform.dataset.query.DatasetResult;
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

import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Endpoints used by the business users' dataset builder.
 * <p>
 * {@code @PreAuthorize} answers "may this user use datasets at all?". Which fields and rows they
 * may see is dynamic, so that is enforced by the catalog / query builder instead.
 */
@RestController
@RequestMapping("/api/datasets")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('PLATFORM_USER')")
class DatasetController {

    private final DatasetCatalog catalog;
    private final DatasetExecutor executor;
    private final IndexAdvisor indexAdvisor;

    /**
     * When the screen opens: every entity with the fields this user may use and the relations to
     * navigate. The UI builds paths from it (root → relation → … → field). No tables/columns exposed.
     */
    @GetMapping("/catalog")
    public CatalogResponse catalog(Authentication authentication) {
        Actor viewer = viewer(authentication);
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
     * "Run" on screen: always paged; returns the page's row count and {@code hasNext}.
     * The dataset's total is off by default: on big data its {@code COUNT(*)} over every matching row
     * costs more than the page itself (perf/README.md). {@code includeTotal=true} turns it on.
     */
    @PostMapping("/run")
    public DatasetResult run(@Valid @RequestBody DatasetDefinition definition,
                            @RequestParam(defaultValue = "0") int page,
                            @RequestParam(required = false) Integer size,
                            @RequestParam(defaultValue = "false") boolean includeTotal,
                            Authentication authentication) {
        return executor.run(definition, viewer(authentication), page, size, includeTotal);
    }

    /**
     * "Download" on screen: CSV streamed row by row instead of one big JSON array.
     * The query is built (and permissions checked) before streaming starts, so errors still map to 4xx.
     */
    @PostMapping("/export")
    public ResponseEntity<StreamingResponseBody> export(@Valid @RequestBody DatasetDefinition definition,
                                                        Authentication authentication) {
        DatasetQuery query = executor.prepare(definition, viewer(authentication));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("dataset.csv").build().toString())
                .contentType(new MediaType("text", "csv", UTF_8))
                .body(outputStream -> executor.writeCsv(query, outputStream));
    }

    /**
     * Shows the generated SQL without executing it, plus index hints for the columns it uses.
     * Reveals schema details, so admins only.
     */
    @PostMapping("/preview")
    @PreAuthorize("hasAuthority('PLATFORM_ADMIN')")
    public SqlPreview preview(@Valid @RequestBody DatasetDefinition definition, Authentication authentication) {
        DatasetQuery query = executor.prepare(definition, viewer(authentication));
        return new SqlPreview(query.sql(), query.parameters(), indexAdvisor.hints(query.columnUsages()));
    }

    /**
     * Index advice collected from the datasets people actually ran: unindexed columns, how often and how
     * slowly they were used, and ready-to-review DDL. Re-reads the database's indexes on each call, so
     * columns indexed meanwhile disappear. Nothing is created automatically.
     * Deliberately open to every dataset user for now (no admin split), so the advice gets seen.
     */
    @GetMapping("/index-advice")
    public List<IndexAdvisor.Advice> indexAdvice() throws SQLException {
        return indexAdvisor.advice();
    }

    static Actor viewer(Authentication authentication) {
        return new Actor(authentication.getName(), authentication.getAuthorities().stream()
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

    record SqlPreview(String sql, List<Object> parameters, List<String> indexHints) {
    }
}
