package com.gucardev.dynamicreportsentity.reporting.web;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end against the real modules and the demo data in data.sql.
 * carol works in Software Development (people 5, 6, 7, 8, 19).
 */
@SpringBootTest
@AutoConfigureMockMvc
class ReportApiIntegrationTest {

    @Autowired
    MockMvcTester mvc;

    private MvcTestResult post(String uri, String body) {
        return mvc.post().uri(uri).contentType(MediaType.APPLICATION_JSON).content(body).exchange();
    }

    private static List<Object> row(Object... values) {
        return Arrays.asList(values);
    }

    private static final String HEADCOUNT_PER_DEPARTMENT = """
            {"root": "Personnel",
             "columns": [{"field": "department.name"}, {"aggregate": "COUNT"}],
             "sort": [{"field": "department.name"}]}
            """;

    @Nested
    @WithMockUser(username = "admin", authorities = {"REPORT_RUN", "SALARY_VIEW", "ALL_DEPARTMENTS"})
    class Relations {

        @Test
        void manyToOneWithAggregationAndHaving() {
            assertThat(post("/api/reports/run", """
                    {"root": "Personnel",
                     "columns": [{"field": "department.name"}, {"aggregate": "COUNT"}],
                     "having": {"aggregate": "COUNT", "operator": "GTE", "value": 3},
                     "sort": [{"field": "department.name"}]}
                    """))
                    .hasStatusOk().bodyJson().extractingPath("$.rows").asArray().containsExactly(
                            row("Finance", 3), row("Human Resources", 3), row("Infrastructure", 3),
                            row("Sales", 4), row("Software Development", 5));
        }

        @Test
        void selfReference() {
            assertThat(post("/api/reports/run", """
                    {"root": "Personnel",
                     "columns": [{"field": "fullName"}],
                     "where": {"field": "manager.fullName", "operator": "EQ", "value": "Carol White"},
                     "sort": [{"field": "fullName"}]}
                    """))
                    .hasStatusOk().bodyJson().extractingPath("$.rows[*][0]").asArray()
                    .containsExactly("Dave Miller", "Erin Garcia", "Frank Wilson", "Quinn Baker");
        }

        @Test
        void oneToOne() {
            assertThat(post("/api/reports/run", """
                    {"root": "Personnel",
                     "columns": [{"aggregate": "COUNT"}],
                     "where": {"field": "profile.city", "operator": "EQ", "value": "Istanbul"}}
                    """))
                    .hasStatusOk().bodyJson().extractingPath("$.rows").asArray().containsExactly(row(8));
        }

        @Test
        void pureManyToManyThroughJunction() {
            assertThat(post("/api/reports/run", """
                    {"root": "Personnel",
                     "columns": [{"field": "fullName"}],
                     "where": {"field": "skills.skill.name", "operator": "EQ", "value": "Kubernetes"},
                     "sort": [{"field": "fullName"}]}
                    """))
                    .hasStatusOk().bodyJson().extractingPath("$.rows[*][0]").asArray()
                    .containsExactly("Bob Jones", "Erin Garcia", "Grace Kim", "Henry Clark");
        }

        @Test
        void manyToManyWithJunctionFields() {
            assertThat(post("/api/reports/run", """
                    {"root": "TrainingRecord",
                     "columns": [{"field": "training.name"}, {"aggregate": "COUNT"}],
                     "where": {"field": "nextRenewalDate", "operator": "NEXT_N_DAYS", "value": 30},
                     "sort": [{"field": "training.name"}]}
                    """))
                    .hasStatusOk().bodyJson().extractingPath("$.rows").asArray().containsExactly(
                            row("Data Protection", 2), row("Fire Safety", 3), row("First Aid", 1),
                            row("Occupational Health and Safety", 6), row("Secure Coding", 1));
        }

        @Test
        void sumOnJunctionFieldWithHavingAndAggregateSort() {
            assertThat(post("/api/reports/run", """
                    {"root": "ProjectAssignment",
                     "columns": [{"field": "project.name"}, {"field": "allocationPercent", "aggregate": "SUM"}],
                     "having": {"field": "allocationPercent", "aggregate": "SUM", "operator": "GT", "value": 150},
                     "sort": [{"field": "allocationPercent", "aggregate": "SUM", "direction": "DESC"}]}
                    """))
                    .hasStatusOk().bodyJson().extractingPath("$.rows").asArray().containsExactly(
                            row("Payroll Modernization", 360), row("Data Center Migration", 270),
                            row("Annual Budget Planning", 190), row("Recruitment Portal", 160));
        }

        @Test
        void nestedOrConditions() {
            assertThat(post("/api/reports/run", """
                    {"root": "Personnel",
                     "columns": [{"field": "fullName"}],
                     "where": {"conditions": [
                                 {"logic": "OR", "conditions": [
                                     {"field": "department.name", "operator": "EQ", "value": "Sales"},
                                     {"field": "salary", "operator": "GT", "value": 90000}]},
                                 {"field": "active", "operator": "EQ", "value": true}]},
                     "sort": [{"field": "fullName"}]}
                    """))
                    .hasStatusOk().bodyJson().extractingPath("$.rows[*][0]").asArray()
                    .containsExactly("Bob Jones", "Diana Prince", "Mia Allen", "Noah King", "Olivia Scott", "Paul Adams");
        }

        @Test
        void hiresPerYear() {
            assertThat(post("/api/reports/run", """
                    {"root": "Personnel",
                     "columns": [{"field": "hireDate", "dateGroup": "YEAR"}, {"aggregate": "COUNT"}],
                     "where": {"field": "hireDate", "dateGroup": "YEAR", "operator": "GTE", "value": 2020},
                     "sort": [{"field": "hireDate", "dateGroup": "YEAR"}]}
                    """))
                    .hasStatusOk().bodyJson().extractingPath("$.rows").asArray().containsExactly(
                            row(2020, 2), row(2021, 2), row(2022, 1), row(2023, 2), row(2024, 2), row(2025, 1));
        }

        @Test
        void assignmentsPerMonth() {
            MvcTestResult result = post("/api/reports/run", """
                    {"root": "ProjectAssignment",
                     "columns": [{"field": "assignedOn", "dateGroup": "MONTH"}, {"aggregate": "COUNT"}],
                     "where": {"field": "assignedOn", "dateGroup": "YEAR", "operator": "EQ", "value": 2025},
                     "sort": [{"field": "assignedOn", "dateGroup": "MONTH"}]}
                    """);
            assertThat(result).hasStatusOk().bodyJson().extractingPath("$.rows").asArray().containsExactly(
                    row("2025-01-01", 2), row("2025-02-01", 2), row("2025-03-01", 5), row("2025-10-01", 2));
            assertThat(result).bodyJson().extractingPath("$.columns[0].label")
                    .isEqualTo("Project Assignment / Assigned On (Month)");
        }

        @Test
        void projectStartsPerQuarter() {
            assertThat(post("/api/reports/run", """
                    {"root": "Project",
                     "columns": [{"field": "startDate", "dateGroup": "QUARTER"}, {"aggregate": "COUNT"}],
                     "sort": [{"field": "startDate", "dateGroup": "QUARTER"}]}
                    """))
                    .hasStatusOk().bodyJson().extractingPath("$.rows").asArray().containsExactly(
                            row("2024-01-01", 1), row("2025-01-01", 2), row("2025-10-01", 1),
                            row("2026-07-01", 1), row("2027-01-01", 1));
        }

        @Test
        void inflatedSumIsRejected() {
            assertThat(post("/api/reports/run", """
                    {"root": "Personnel",
                     "columns": [{"field": "trainingRecords.training.name"}, {"field": "salary", "aggregate": "SUM"}]}
                    """))
                    .hasStatus(HttpStatus.BAD_REQUEST)
                    .bodyJson().extractingPath("$.detail").asString().contains("would be inflated");
        }

        @Test
        void pagesWithHasNextFlag() {
            MvcTestResult first = post("/api/reports/run?page=0&size=5", HEADCOUNT_PER_DEPARTMENT);
            assertThat(first).bodyJson().extractingPath("$.rows").asArray().hasSize(5);
            assertThat(first).bodyJson().extractingPath("$.hasNext").isEqualTo(true);

            MvcTestResult second = post("/api/reports/run?page=1&size=5", HEADCOUNT_PER_DEPARTMENT);
            assertThat(second).bodyJson().extractingPath("$.rows").asArray()
                    .containsExactly(row("Sales", 4), row("Software Development", 5));
            assertThat(second).bodyJson().extractingPath("$.hasNext").isEqualTo(false);
        }

        @Test
        void pageSizeIsCapped() {
            assertThat(post("/api/reports/run?size=1000000", HEADCOUNT_PER_DEPARTMENT))
                    .hasStatusOk().bodyJson().extractingPath("$.size").isEqualTo(500);
        }
    }

    @Nested
    class RowLevelSecurity {

        @Test
        @WithMockUser(username = "carol", authorities = "REPORT_RUN")
        void onlyOwnDepartment() {
            assertThat(post("/api/reports/run", """
                    {"root": "Personnel", "columns": [{"field": "fullName"}], "sort": [{"field": "fullName"}]}
                    """))
                    .hasStatusOk().bodyJson().extractingPath("$.rows[*][0]").asArray()
                    .containsExactly("Carol White", "Dave Miller", "Erin Garcia", "Frank Wilson", "Quinn Baker");
        }

        @Test
        @WithMockUser(username = "carol", authorities = "REPORT_RUN")
        void ownedEntitiesFollowTheirOwner() {
            assertThat(post("/api/reports/run", """
                    {"root": "TrainingRecord", "columns": [{"aggregate": "COUNT"}]}
                    """))
                    .hasStatusOk().bodyJson().extractingPath("$.rows").asArray().containsExactly(row(13));

            assertThat(post("/api/reports/run", """
                    {"root": "ProjectAssignment",
                     "columns": [{"field": "project.name"}, {"field": "allocationPercent", "aggregate": "SUM"}],
                     "having": {"field": "allocationPercent", "aggregate": "SUM", "operator": "GT", "value": 150}}
                    """))
                    .hasStatusOk().bodyJson().extractingPath("$.rows").asArray()
                    .containsExactly(row("Payroll Modernization", 330));
        }

        @Test
        @WithMockUser(username = "carol", authorities = "REPORT_RUN")
        void hiddenPeopleShowUpAsEmptyInRelatedData() {
            assertThat(post("/api/reports/run", """
                    {"root": "Department",
                     "columns": [{"field": "name"}, {"field": "manager.fullName"}],
                     "sort": [{"field": "name"}]}
                    """))
                    .hasStatusOk().bodyJson().extractingPath("$.rows").asArray().containsExactly(
                            row("Finance", null), row("Head Office", null), row("Human Resources", null),
                            row("Information Technology", null), row("Infrastructure", null), row("Sales", null),
                            row("Software Development", "Carol White"));
        }

        @Test
        @WithMockUser(username = "admin", authorities = {"REPORT_RUN", "ALL_DEPARTMENTS"})
        void allDepartmentsSeesEverything() {
            assertThat(post("/api/reports/run", """
                    {"root": "TrainingRecord", "columns": [{"aggregate": "COUNT"}]}
                    """))
                    .hasStatusOk().bodyJson().extractingPath("$.rows").asArray().containsExactly(row(50));
        }

        @Test
        @WithMockUser(username = "stranger", authorities = "REPORT_RUN")
        void userWithoutDepartmentIsForbidden() {
            assertThat(post("/api/reports/run", HEADCOUNT_PER_DEPARTMENT)).hasStatus(HttpStatus.FORBIDDEN);
        }
    }

    @Nested
    class EndpointAndFieldAccess {

        @Test
        void anonymousIsRejected() {
            assertThat(post("/api/reports/run", HEADCOUNT_PER_DEPARTMENT)).hasStatus(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @WithMockUser(username = "admin", authorities = "SOMETHING_ELSE")
        void userWithoutReportRunIsForbidden() {
            assertThat(post("/api/reports/run", HEADCOUNT_PER_DEPARTMENT)).hasStatus(HttpStatus.FORBIDDEN);
        }

        @Test
        @WithMockUser(username = "alice", authorities = "REPORT_RUN")
        void previewIsAdminOnly() {
            assertThat(post("/api/reports/preview", HEADCOUNT_PER_DEPARTMENT)).hasStatus(HttpStatus.FORBIDDEN);
        }

        @Test
        @WithMockUser(username = "admin", authorities = {"REPORT_RUN", "REPORT_ADMIN", "ALL_DEPARTMENTS"})
        void previewShowsSql() {
            assertThat(post("/api/reports/preview", HEADCOUNT_PER_DEPARTMENT))
                    .hasStatusOk().bodyJson().extractingPath("$.sql").asString()
                    .contains("LEFT JOIN department t1 ON t1.id = t0.department_id", "GROUP BY t1.name");
        }

        @Test
        @WithMockUser(username = "alice", authorities = "REPORT_RUN")
        void catalogHidesFieldsTheUserMayNotUseAndListsRelations() {
            var personnel = assertThat(mvc.get().uri("/api/reports/catalog")).hasStatusOk().bodyJson();
            personnel.extractingPath("$.entities[?(@.name == 'Personnel')].fields[*].name").asArray()
                    .contains("fullName", "hireDate", "active")
                    .doesNotContain("salary", "nationalId", "departmentId");
            assertThat(mvc.get().uri("/api/reports/catalog")).bodyJson()
                    .extractingPath("$.entities[?(@.name == 'Personnel')].relations[*].name").asArray()
                    .contains("department", "manager", "directReports", "profile", "skills", "trainingRecords",
                            "projectAssignments", "managedDepartments");
        }

        @Test
        @WithMockUser(username = "alice", authorities = "REPORT_RUN")
        void hiddenFieldIsForbiddenInFiltersToo() {
            assertThat(post("/api/reports/run", """
                    {"root": "Department", "columns": [{"field": "name"}],
                     "where": {"field": "members.salary", "operator": "GT", "value": 50000}}
                    """))
                    .hasStatus(HttpStatus.FORBIDDEN)
                    .bodyJson().extractingPath("$.detail")
                    .isEqualTo("You are not allowed to use field: Department / Members / Salary");
        }
    }

    @Nested
    class Export {

        @Test
        @WithMockUser(username = "carol", authorities = "REPORT_RUN")
        void streamsCsvWithRowSecurityApplied() {
            MvcTestResult result = post("/api/reports/export", """
                    {"root": "Personnel",
                     "columns": [{"field": "fullName"}, {"field": "manager.fullName", "label": "Manager"}],
                     "sort": [{"field": "fullName"}]}
                    """);

            assertThat(result).hasStatusOk().hasContentType("text/csv;charset=UTF-8");
            assertThat(result).bodyText().isEqualTo("﻿Personnel / Full Name,Manager\r\n"
                    + "Carol White,\r\n"
                    + "Dave Miller,Carol White\r\n"
                    + "Erin Garcia,Carol White\r\n"
                    + "Frank Wilson,Carol White\r\n"
                    + "Quinn Baker,Carol White\r\n");
        }
    }

    @Nested
    @WithMockUser(username = "admin", authorities = {"REPORT_RUN", "ALL_DEPARTMENTS"})
    class SqlInjection {

        @Test
        void injectionInValuesIsJustAValue() {
            assertThat(post("/api/reports/run", """
                    {"root": "Personnel", "columns": [{"field": "fullName"}],
                     "where": {"logic": "OR", "conditions": [
                         {"field": "fullName", "operator": "EQ", "value": "x' OR '1'='1"},
                         {"field": "fullName", "operator": "CONTAINS", "value": "'; DROP TABLE personnel; --"}]}}
                    """))
                    .hasStatusOk().bodyJson().extractingPath("$.rows").asArray().isEmpty();

            assertThat(post("/api/reports/run", """
                    {"root": "Personnel", "columns": [{"aggregate": "COUNT"}]}
                    """))
                    .hasStatusOk().bodyJson().extractingPath("$.rows").asArray().containsExactly(row(20));
        }

        @Test
        void injectionInIdentifiersIsRejected() {
            assertThat(post("/api/reports/run", """
                    {"root": "Personnel; DROP TABLE personnel", "columns": [{"field": "fullName"}]}
                    """)).hasStatus(HttpStatus.BAD_REQUEST);
            assertThat(post("/api/reports/run", """
                    {"root": "Personnel", "columns": [{"field": "fullName FROM personnel; --"}]}
                    """)).hasStatus(HttpStatus.BAD_REQUEST);
            assertThat(post("/api/reports/run", """
                    {"root": "Personnel", "columns": [{"field": "fullName"}],
                     "sort": [{"field": "fullName", "direction": "DESC; DROP TABLE personnel"}]}
                    """)).hasStatus(HttpStatus.BAD_REQUEST);
            assertThat(post("/api/reports/run", """
                    {"root": "Personnel", "columns": [{"field": "fullName"}],
                     "where": {"field": "fullName", "operator": "= 1 OR 1=1 --", "value": "x"}}
                    """)).hasStatus(HttpStatus.BAD_REQUEST);
        }

        @Test
        void validationErrors() {
            assertThat(post("/api/reports/run", """
                    {"root": "Personnel", "columns": []}
                    """)).hasStatus(HttpStatus.BAD_REQUEST);
            assertThat(post("/api/reports/run", """
                    {"root": "Personnel", "columns": [{"field": "nationalId"}]}
                    """))
                    .hasStatus(HttpStatus.BAD_REQUEST)
                    .bodyJson().extractingPath("$.detail").isEqualTo("Unknown field: nationalId");
        }
    }
}
