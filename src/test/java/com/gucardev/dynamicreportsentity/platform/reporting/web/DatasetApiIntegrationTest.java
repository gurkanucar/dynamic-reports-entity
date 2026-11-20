package com.gucardev.dynamicreportsentity.platform.reporting.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

/**
 * Datasets end to end, on the demo data (data.sql). Every test uses its own dataset name: the context and
 * its database are shared with the other integration tests.
 */
@SpringBootTest
@AutoConfigureMockMvc
class DatasetApiIntegrationTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static final RequestPostProcessor ADMIN = as("admin", "REPORT_RUN", "REPORT_ADMIN", "SALARY_VIEW",
            "ALL_DEPARTMENTS");
    /** Sees every department, may not see salaries. */
    private static final RequestPostProcessor AUDITOR = as("auditor", "REPORT_RUN", "ALL_DEPARTMENTS");
    /** Row-level security: only Software Development. */
    private static final RequestPostProcessor CAROL = as("carol", "REPORT_RUN");

    /** Every kind of cell: text, date, number, boolean, a LIST of children and a relative date filter. */
    private static final String PEOPLE = """
            {"root": "Personnel",
             "columns": [{"field": "fullName"}, {"field": "hireDate"}, {"field": "salary"}, {"field": "active"},
                         {"field": "department.name"}, {"field": "trainingRecords.training.name", "aggregate": "LIST"}],
             "where": {"field": "hireDate", "operator": "LAST_N_DAYS", "value": 100000},
             "sort": [{"field": "fullName"}]}
            """;

    private static final String HEADCOUNT = """
            {"root": "Personnel",
             "columns": [{"field": "department.name"}, {"aggregate": "COUNT"}],
             "sort": [{"field": "department.name"}]}
            """;

    private static final Comparator<JsonNode> NUMBERS_BY_VALUE = (a, b) -> a.isNumber() && b.isNumber()
            ? a.decimalValue().compareTo(b.decimalValue()) : a.equals(b) ? 0 : 1;

    @Autowired
    MockMvcTester mvc;

    private static RequestPostProcessor as(String name, String... authorities) {
        return user(name).authorities(Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList());
    }

    private MvcTestResult create(String name, String mode, Integer hours, String definition) {
        return mvc.post().uri("/api/datasets").with(ADMIN).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "%s", "label": "Test %s", "mode": "%s", "refreshEveryHours": %s, "definition": %s}
                        """.formatted(name, name, mode, hours, definition)).exchange();
    }

    private MvcTestResult refresh(String name) {
        return mvc.post().uri("/api/datasets/" + name + "/refresh").with(ADMIN).exchange();
    }

    private MvcTestResult rows(String name, RequestPostProcessor reader) {
        return mvc.get().uri("/api/datasets/" + name + "/rows?size=100").with(reader).exchange();
    }

    private MvcTestResult run(String definition, RequestPostProcessor reader) {
        return mvc.post().uri("/api/reports/run?size=100").with(reader).contentType(MediaType.APPLICATION_JSON)
                .content(definition).exchange();
    }

    private static JsonNode json(MvcTestResult result) {
        return JSON.readTree(result.getResponse().getContentAsByteArray());
    }

    @Test
    void snapshotServesExactlyWhatTheReportReturns() {
        assertThat(create("t_people", "SNAPSHOT", null, PEOPLE)).hasStatus(HttpStatus.CREATED);
        MvcTestResult refreshed = refresh("t_people");
        assertThat(refreshed).hasStatusOk();
        assertThat(json(refreshed).get("status").asString()).isEqualTo("READY");
        assertThat(json(refreshed).get("rowCount").asLong()).isEqualTo(20);

        JsonNode live = json(run(PEOPLE, ADMIN));
        JsonNode snapshot = json(rows("t_people", ADMIN));
        assertThat(snapshot.get("mode").asString()).isEqualTo("SNAPSHOT");
        assertThat(snapshot.get("refreshedAt").isNull()).isFalse();
        // same values; numbers compared by value (H2 stores the snapshot as DECFLOAT: 72000.0 comes back as 72000)
        assertThat(snapshot.get("result").get("rows").equals(NUMBERS_BY_VALUE, live.get("rows"))).isTrue();
        assertThat(snapshot.get("result").get("columns")).isEqualTo(live.get("columns"));
        // the snapshot knows its size: total comes for free
        assertThat(snapshot.get("result").get("total").asLong()).isEqualTo(20);
    }

    @Test
    void snapshotPagesKeepTheReportOrder() {
        create("t_paged", "SNAPSHOT", null, HEADCOUNT);
        refresh("t_paged");

        JsonNode first = json(mvc.get().uri("/api/datasets/t_paged/rows?size=5").with(ADMIN).exchange()).get("result");
        JsonNode second = json(mvc.get().uri("/api/datasets/t_paged/rows?size=5&page=1").with(ADMIN).exchange())
                .get("result");
        JsonNode all = json(run(HEADCOUNT, ADMIN)).get("rows");

        assertThat(first.get("count").asInt()).isEqualTo(5);
        assertThat(first.get("hasNext").asBoolean()).isTrue();
        assertThat(second.get("count").asInt()).isEqualTo(2);
        assertThat(second.get("hasNext").asBoolean()).isFalse();
        assertThat(first.get("rows").get(0)).isEqualTo(all.get(0));
        assertThat(second.get("rows").get(1)).isEqualTo(all.get(6));
    }

    @Test
    void snapshotIsNotServedToReadersNarrowedByRowSecurity() {
        create("t_restricted", "SNAPSHOT", null, HEADCOUNT);
        refresh("t_restricted");

        assertThat(rows("t_restricted", CAROL)).hasStatus(HttpStatus.FORBIDDEN)
                .bodyJson().extractingPath("$.detail").asString().contains("row-level security", "LIVE");
        assertThat(rows("t_restricted", AUDITOR)).hasStatusOk();
    }

    @Test
    void snapshotChecksFieldPermissionsOfTheReader() {
        create("t_salaries", "SNAPSHOT", null, PEOPLE);
        refresh("t_salaries");

        assertThat(rows("t_salaries", AUDITOR)).hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void liveDatasetRunsAsTheReader() {
        assertThat(create("t_live", "LIVE", null, HEADCOUNT)).hasStatus(HttpStatus.CREATED)
                .bodyJson().extractingPath("$.status").isEqualTo("READY");

        JsonNode carolsRows = json(rows("t_live", CAROL));
        assertThat(carolsRows.get("mode").asString()).isEqualTo("LIVE");
        assertThat(carolsRows.get("result").get("rows")).isEqualTo(json(run(HEADCOUNT, CAROL)).get("rows"));
        assertThat(carolsRows.get("result").get("rows").size()).isEqualTo(1);   // her department only
        assertThat(json(rows("t_live", ADMIN)).get("result").get("rows").size()).isEqualTo(7);
    }

    @Test
    void refreshPeriodSchedulesTheNextRefresh() {
        create("t_scheduled", "SNAPSHOT", 6, HEADCOUNT);
        JsonNode info = json(refresh("t_scheduled"));

        Instant refreshedAt = Instant.parse(info.get("refreshedAt").asString());
        assertThat(info.get("refreshEveryHours").asInt()).isEqualTo(6);
        assertThat(Instant.parse(info.get("nextRefreshAt").asString())).isEqualTo(refreshedAt.plusSeconds(6 * 3600));
    }

    @Test
    void switchingToLiveDropsTheSnapshot() {
        create("t_switch", "SNAPSHOT", 12, HEADCOUNT);
        refresh("t_switch");

        MvcTestResult updated = mvc.put().uri("/api/datasets/t_switch").with(ADMIN).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"mode": "LIVE", "definition": %s}""".formatted(HEADCOUNT)).exchange();
        assertThat(updated).hasStatusOk();
        assertThat(json(updated).get("rowCount").isNull()).isTrue();
        assertThat(json(updated).get("nextRefreshAt").isNull()).isTrue();
        assertThat(json(rows("t_switch", CAROL)).get("result").get("rows").size()).isEqualTo(1);
        assertThat(refresh("t_switch")).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void deleteRemovesTheDataset() {
        create("t_deleted", "SNAPSHOT", null, HEADCOUNT);
        refresh("t_deleted");

        assertThat(mvc.delete().uri("/api/datasets/t_deleted").with(ADMIN).exchange()).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(mvc.get().uri("/api/datasets/t_deleted").with(ADMIN).exchange()).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(rows("t_deleted", ADMIN)).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void listShowsDefinitionsAndState() {
        create("t_listed", "LIVE", null, HEADCOUNT);

        assertThat(mvc.get().uri("/api/datasets").with(CAROL).exchange()).hasStatusOk()
                .bodyJson().extractingPath("$[?(@.name == 't_listed')].definition.root").asArray()
                .containsExactly("Personnel");
    }

    @Test
    void onlyReportAdminsDefineAndRefresh() {
        assertThat(mvc.post().uri("/api/datasets").with(CAROL).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "t_carol", "mode": "LIVE", "definition": %s}""".formatted(HEADCOUNT)).exchange())
                .hasStatus(HttpStatus.FORBIDDEN);
        create("t_admin_only", "SNAPSHOT", null, HEADCOUNT);
        assertThat(mvc.post().uri("/api/datasets/t_admin_only/refresh").with(CAROL).exchange())
                .hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void invalidDefinitionsAreRejected() {
        assertThat(create("Bad-Name", "LIVE", null, HEADCOUNT)).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(create("t_hours_live", "LIVE", 3, HEADCOUNT)).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(create("t_hours_zero", "SNAPSHOT", 0, HEADCOUNT)).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(create("t_unknown_field", "SNAPSHOT", null, """
                {"root": "Personnel", "columns": [{"field": "nope"}]}""")).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(mvc.get().uri("/api/datasets/t_missing/rows").with(ADMIN).exchange())
                .hasStatus(HttpStatus.NOT_FOUND);

        create("t_twice", "LIVE", null, HEADCOUNT);
        assertThat(create("t_twice", "LIVE", null, HEADCOUNT)).hasStatus(HttpStatus.CONFLICT);
    }

    @Test
    void editorMayOnlySaveFieldsTheyMayUse() {
        assertThat(mvc.post().uri("/api/datasets").with(as("editor", "REPORT_RUN", "REPORT_ADMIN", "ALL_DEPARTMENTS"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "t_no_salary", "mode": "SNAPSHOT", "definition": %s}""".formatted(PEOPLE)).exchange())
                .hasStatus(HttpStatus.FORBIDDEN);
    }
}
