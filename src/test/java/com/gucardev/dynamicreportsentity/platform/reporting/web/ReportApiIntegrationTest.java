package com.gucardev.dynamicreportsentity.platform.reporting.web;

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
 * End-to-end against the real modules and the demo data in db/seed/h2/demo.
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

        /** Showcase #28 of reports.http: 7 joins (department four times), 3 group columns, 8 aggregates. */
        @Test
        void divisionStaffingScorecard() {
            MvcTestResult result = post("/api/reports/run", """
                    {"root": "ProjectAssignment",
                     "columns": [
                       {"field": "project.department.parent.name"}, {"field": "project.status"},
                       {"field": "assignedOn", "dateGroup": "YEAR"},
                       {"aggregate": "COUNT"},
                       {"field": "personnel.email", "aggregate": "COUNT_DISTINCT"},
                       {"field": "project.code", "aggregate": "COUNT_DISTINCT"},
                       {"field": "allocationPercent", "aggregate": "SUM"},
                       {"field": "allocationPercent", "aggregate": "AVG"},
                       {"field": "personnel.salary", "aggregate": "AVG"},
                       {"field": "personnel.hireDate", "aggregate": "MIN"},
                       {"field": "project.budget", "aggregate": "MAX"}],
                     "where": {"conditions": [
                       {"field": "project.status", "operator": "NOT_IN", "value": ["COMPLETED"]},
                       {"logic": "OR", "conditions": [
                         {"field": "personnel.department.parent.name", "operator": "EQ", "value": "Information Technology"},
                         {"field": "project.budget", "operator": "GT", "value": 100000}]},
                       {"field": "assignedOn", "dateGroup": "YEAR", "operator": "GTE", "value": 2024}]},
                     "having": {"conditions": [
                       {"aggregate": "COUNT", "operator": "GTE", "value": 5},
                       {"field": "allocationPercent", "aggregate": "SUM", "operator": "GT", "value": 200}]},
                     "sort": [{"field": "allocationPercent", "aggregate": "SUM", "direction": "DESC"}]}
                    """);

            // Payroll Modernization (5 people, 360%) + Data Center Migration (4 people, 270%), both owned under IT, 2025
            assertThat(result).hasStatusOk().bodyJson().extractingPath("$.rows").asArray().hasSize(1);
            assertThat(result).bodyJson().extractingPath("$.rows[0][0:7]").asArray()
                    .containsExactly("Information Technology", "ACTIVE", 2025, 9, 9, 2, 630);
            assertThat(result).bodyJson().extractingPath("$.rows[0][9]").isEqualTo("2014-01-15");
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
                    .hasStatusOk().bodyJson().extractingPath("$.size").isEqualTo(5000);
        }

        @Test
        void countAndTotalOnRequest() {
            // first page with a next page: total comes from the COUNT query
            MvcTestResult first = post("/api/reports/run?size=5&includeTotal=true", HEADCOUNT_PER_DEPARTMENT);
            assertThat(first).bodyJson().extractingPath("$.count").isEqualTo(5);
            assertThat(first).bodyJson().extractingPath("$.total").isEqualTo(7);

            // last page: total derived from the page, no second query
            MvcTestResult last = post("/api/reports/run?size=5&page=1&includeTotal=true", HEADCOUNT_PER_DEPARTMENT);
            assertThat(last).bodyJson().extractingPath("$.count").isEqualTo(2);
            assertThat(last).bodyJson().extractingPath("$.total").isEqualTo(7);

            // past the end: empty page, total still correct
            MvcTestResult beyond = post("/api/reports/run?size=5&page=9&includeTotal=true", HEADCOUNT_PER_DEPARTMENT);
            assertThat(beyond).bodyJson().extractingPath("$.count").isEqualTo(0);
            assertThat(beyond).bodyJson().extractingPath("$.total").isEqualTo(7);

            MvcTestResult joined = post("/api/reports/run?size=3&includeTotal=true", """
                    {"root": "Personnel",
                     "columns": [{"field": "fullName"}, {"field": "manager.fullName"}, {"field": "department.name"}],
                     "where": {"field": "active", "operator": "EQ", "value": true}}
                    """);
            assertThat(joined).bodyJson().extractingPath("$.total").isEqualTo(18);
            assertThat(joined).bodyJson().extractingPath("$.count").isEqualTo(3);
        }

        @Test
        void noTotalByDefault() {
            MvcTestResult result = post("/api/reports/run?size=5", HEADCOUNT_PER_DEPARTMENT);
            assertThat(result).bodyJson().extractingPath("$.total").isNull();
            assertThat(result).bodyJson().extractingPath("$.count").isEqualTo(5);
            assertThat(result).bodyJson().extractingPath("$.hasNext").isEqualTo(true);
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
                    .contains("id", "username", "fullName", "hireDate", "active")
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
    @WithMockUser(username = "alice", authorities = "REPORT_RUN")
    class IndexAdvice {

        private static final String UNINDEXED_FILTERS = """
                {"root": "TrainingRecord",
                 "columns": [{"field": "personnel.fullName"}],
                 "where": {"conditions": [
                     {"field": "personnel.email", "operator": "CONTAINS", "value": "example"},
                     {"field": "score", "operator": "GT", "value": 50},
                     {"field": "completedOn", "dateGroup": "YEAR", "operator": "GTE", "value": 2020}]}}
                """;

        @Test
        void runResponseCarriesHintsForUnindexedColumns() {
            assertThat(post("/api/reports/run", UNINDEXED_FILTERS))
                    .hasStatusOk().bodyJson().extractingPath("$.indexHints").asArray()
                    .anySatisfy(hint -> assertThat((String) hint).startsWith("personnel.email [FILTER_CONTAINS]").contains("gin_trgm_ops"))
                    .anySatisfy(hint -> assertThat((String) hint).contains("CREATE INDEX ix_training_record_score ON training_record (score);"))
                    .anySatisfy(hint -> assertThat((String) hint).startsWith("training_record.completed_on [FILTER_EXPRESSION]"));
        }

        @Test
        void indexedReportHasNoHints() {
            assertThat(post("/api/reports/run", """
                    {"root": "TrainingRecord", "columns": [{"field": "personnel.department.name"}],
                     "where": {"field": "nextRenewalDate", "operator": "NEXT_N_DAYS", "value": 30}}
                    """))
                    .hasStatusOk().bodyJson().extractingPath("$.indexHints").asArray().isEmpty();
        }

        @Test
        void adviceCollectsWhatWasRunAndIsOpenToReportUsers() {
            post("/api/reports/run", UNINDEXED_FILTERS);

            assertThat(mvc.get().uri("/api/reports/index-advice"))
                    .hasStatusOk().bodyJson().extractingPath("$[*].column").asArray()
                    .contains("email", "score", "completed_on")
                    .doesNotContain("next_renewal_date", "personnel_id", "department_id");
        }
    }

    @Nested
    @WithMockUser(username = "admin", authorities = {"REPORT_RUN", "ALL_DEPARTMENTS"})
    class NestedLists {

        private static final String PEOPLE_WITH_SKILLS_AND_TRAININGS = """
                {"root": "Personnel",
                 "columns": [
                   {"field": "fullName"},
                   {"field": "skills.skill.name", "aggregate": "LIST", "label": "Skills"},
                   {"field": "trainingRecords.training.name", "aggregate": "LIST", "label": "Trainings"},
                   {"field": "projectAssignments.project.code", "aggregate": "LIST", "label": "Projects"}],
                 "where": {"field": "department.name", "operator": "EQ", "value": "Software Development"},
                 "sort": [{"field": "fullName"}]}
                """;

        @Test
        void eachPersonGetsArraysFromThreeOneToManyRelations() {
            MvcTestResult result = post("/api/reports/run", PEOPLE_WITH_SKILLS_AND_TRAININGS);

            assertThat(result).hasStatusOk().bodyJson().extractingPath("$.rows").asArray().containsExactly(
                    row("Carol White", List.of("Java", "React", "SQL"),
                            List.of("Data Protection", "Leadership Fundamentals", "Occupational Health and Safety", "Secure Coding"),
                            List.of("PRJ-001", "PRJ-005")),
                    row("Dave Miller", List.of("Java", "React"),
                            List.of("Data Protection", "Occupational Health and Safety", "Secure Coding"), List.of("PRJ-001")),
                    row("Erin Garcia", List.of("Java", "Kubernetes", "SQL"),
                            List.of("Occupational Health and Safety", "Secure Coding"), List.of("PRJ-001")),
                    row("Frank Wilson", List.of("React"),
                            List.of("Occupational Health and Safety", "Secure Coding"), List.of("PRJ-003")),
                    row("Quinn Baker", List.of("Java"),
                            List.of("Occupational Health and Safety", "Secure Coding"), List.of("PRJ-001")));
            assertThat(result).bodyJson().extractingPath("$.columns[1].aggregate").isEqualTo("LIST");
        }

        @Test
        void noChildrenGivesAnEmptyArray() {
            assertThat(post("/api/reports/run", """
                    {"root": "Personnel",
                     "columns": [{"field": "fullName"}, {"field": "skills.skill.name", "aggregate": "LIST"}],
                     "where": {"field": "fullName", "operator": "EQ", "value": "Paul Adams"}}
                    """))
                    .hasStatusOk().bodyJson().extractingPath("$.rows").asArray()
                    .containsExactly(row("Paul Adams", List.of()));
        }

        @Test
        @WithMockUser(username = "carol", authorities = "REPORT_RUN")
        void listsRespectRowSecurity() {
            assertThat(post("/api/reports/run", """
                    {"root": "Department",
                     "columns": [{"field": "name"}, {"field": "members.fullName", "aggregate": "LIST"}],
                     "where": {"field": "name", "operator": "IN", "value": ["Finance", "Software Development"]},
                     "sort": [{"field": "name"}]}
                    """))
                    .hasStatusOk().bodyJson().extractingPath("$.rows").asArray().containsExactly(
                            row("Finance", List.of()),
                            row("Software Development",
                                    List.of("Carol White", "Dave Miller", "Erin Garcia", "Frank Wilson", "Quinn Baker")));
        }

        @Test
        void csvPutsTheListInOneCell() {
            assertThat(post("/api/reports/export", """
                    {"root": "Department",
                     "columns": [{"field": "name"}, {"field": "members.fullName", "aggregate": "LIST", "label": "Members"}],
                     "where": {"field": "name", "operator": "EQ", "value": "Finance"}}
                    """))
                    .hasStatusOk().bodyText()
                    .isEqualTo("﻿Department / Name,Members\r\nFinance,Jack Moore; Kate Hall; Liam Young\r\n");
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
                    {"root": "Personnel", "columns": [{"field": "doesNotExist"}]}
                    """))
                    .hasStatus(HttpStatus.BAD_REQUEST)
                    .bodyJson().extractingPath("$.detail").isEqualTo("Unknown field: doesNotExist");
        }
    }

    @Nested
    @WithMockUser(username = "admin", authorities = {"REPORT_RUN", "ALL_DEPARTMENTS"})
    class FormerLimits {

        @Test
        void fieldToFieldComparison() {
            // people working on a project owned by another department than their own
            assertThat(post("/api/reports/run", """
                    {"root": "ProjectAssignment",
                     "columns": [{"field": "personnel.fullName"}, {"field": "project.code"}],
                     "where": {"field": "personnel.department.id", "operator": "NOT_EQ", "otherField": "project.department.id"},
                     "sort": [{"field": "project.code"}, {"field": "personnel.fullName"}]}
                    """))
                    .hasStatusOk().bodyJson().extractingPath("$.rows").asArray().containsExactly(
                            row("Kate Hall", "PRJ-001"), row("Bob Jones", "PRJ-002"), row("Frank Wilson", "PRJ-003"),
                            row("Carol White", "PRJ-005"), row("Henry Clark", "PRJ-006"));
        }

        @Test
        void notExists() {
            // IT division people WITHOUT a Fire Safety training
            assertThat(post("/api/reports/run", """
                    {"root": "Personnel",
                     "columns": [{"field": "fullName"}],
                     "where": {"conditions": [
                       {"field": "department.parent.name", "operator": "EQ", "value": "Information Technology"},
                       {"relation": "trainingRecords", "operator": "NOT_EXISTS",
                        "match": {"field": "training.name", "operator": "EQ", "value": "Fire Safety"}}]},
                     "sort": [{"field": "fullName"}]}
                    """))
                    .hasStatusOk().bodyJson().extractingPath("$.rows[*][0]").asArray().containsExactly(
                            "Carol White", "Dave Miller", "Erin Garcia", "Frank Wilson", "Ivy Turner", "Quinn Baker");
        }

        @Test
        void exists() {
            assertThat(post("/api/reports/run", """
                    {"root": "Personnel", "columns": [{"field": "fullName"}],
                     "where": {"relation": "trainingRecords", "operator": "EXISTS",
                               "match": {"field": "status", "operator": "EQ", "value": "FAILED"}}}
                    """))
                    .hasStatusOk().bodyJson().extractingPath("$.rows[*][0]").asArray().containsExactly("Frank Wilson");
        }

        @Test
        void listOfObjects() {
            MvcTestResult result = post("/api/reports/run", """
                    {"root": "Personnel",
                     "columns": [{"field": "fullName"},
                                 {"field": "trainingRecords", "aggregate": "LIST", "fields": ["training.name", "status"]}],
                     "where": {"field": "username", "operator": "EQ", "value": "carol"}}
                    """);
            assertThat(result).hasStatusOk().bodyJson().extractingPath("$.rows[0][1]").asArray().hasSize(4);
            assertThat(result).bodyJson().extractingPath("$.rows[0][1][0]")
                    .isEqualTo(java.util.Map.of("training.name", "Data Protection", "status", "PASSED"));
            assertThat(result).bodyJson().extractingPath("$.columns[1].keys").asArray().containsExactly("training.name", "status");
        }

        @Test
        void filteredAndSortedList() {
            assertThat(post("/api/reports/run", """
                    {"root": "Personnel",
                     "columns": [{"field": "fullName"},
                                 {"field": "trainingRecords.training.name", "aggregate": "LIST", "order": "DESC",
                                  "where": {"field": "status", "operator": "EQ", "value": "PASSED"}}],
                     "where": {"field": "username", "operator": "IN", "value": ["carol", "frank"]},
                     "sort": [{"field": "fullName"}]}
                    """))
                    .hasStatusOk().bodyJson().extractingPath("$.rows").asArray().containsExactly(
                            row("Carol White", List.of("Secure Coding", "Occupational Health and Safety",
                                    "Leadership Fundamentals", "Data Protection")),
                            row("Frank Wilson", List.of("Occupational Health and Safety")));
        }

        @Test
        void limitedListIsReportedAsTruncated() {
            MvcTestResult result = post("/api/reports/run", """
                    {"root": "Department",
                     "columns": [{"field": "name"},
                                 {"field": "members.fullName", "aggregate": "LIST", "limit": 2, "label": "Members"}],
                     "where": {"field": "name", "operator": "EQ", "value": "Finance"}}
                    """);
            assertThat(result).hasStatusOk().bodyJson().extractingPath("$.rows[0][1]").asArray()
                    .containsExactly("Jack Moore", "Kate Hall");
            assertThat(result).bodyJson().extractingPath("$.truncatedLists").asArray().containsExactly("Members");
        }

        @Test
        void perRootAggregatesWithListAndHaving() {
            assertThat(post("/api/reports/run", """
                    {"root": "Department", "perRoot": true,
                     "columns": [{"field": "name"},
                                 {"field": "members.fullName", "aggregate": "COUNT", "label": "Headcount"},
                                 {"field": "projects.code", "aggregate": "LIST", "label": "Projects"}],
                     "having": {"field": "members.fullName", "aggregate": "COUNT", "operator": "GTE", "value": 3},
                     "sort": [{"field": "members.fullName", "aggregate": "COUNT", "direction": "DESC"}]}
                    """))
                    .hasStatusOk().bodyJson().extractingPath("$.rows").asArray().containsExactly(
                            row("Software Development", 5, List.of("PRJ-001")),
                            row("Sales", 4, List.of("PRJ-005")),
                            row("Human Resources", 3, List.of("PRJ-003")),
                            row("Infrastructure", 3, List.of("PRJ-002")),
                            row("Finance", 3, List.of("PRJ-004")));
        }

        @Test
        void perRootCountOfFilteredChildren() {
            assertThat(post("/api/reports/run", """
                    {"root": "Personnel", "perRoot": true,
                     "columns": [{"field": "fullName"},
                                 {"field": "trainingRecords.status", "aggregate": "COUNT", "label": "Failed",
                                  "where": {"field": "status", "operator": "EQ", "value": "FAILED"}}],
                     "where": {"field": "username", "operator": "IN", "value": ["carol", "frank"]},
                     "sort": [{"field": "fullName"}]}
                    """))
                    .hasStatusOk().bodyJson().extractingPath("$.rows").asArray()
                    .containsExactly(row("Carol White", 0), row("Frank Wilson", 1));
        }

        @Test
        void includeAllFieldsExposesEveryPlainFieldExceptHidden() {
            var fields = assertThat(mvc.get().uri("/api/reports/catalog")).hasStatusOk().bodyJson();
            fields.extractingPath("$.entities[?(@.name == 'Training')].fields[*].name").asArray()
                    .containsExactly("id", "name", "category", "validityMonths", "mandatory");
            assertThat(mvc.get().uri("/api/reports/catalog")).bodyJson()
                    .extractingPath("$.entities[?(@.name == 'Training')].fields[*].label").asArray()
                    .containsExactly("ID", "Name", "Category", "Validity (months)", "Mandatory");
        }
    }

    @Nested
    class FieldVersusAggregate {

        private static final String ABOVE_DEPARTMENT_AVERAGE = """
                {"root": "Personnel", "columns": [{"field": "fullName"}],
                 "where": {"field": "salary", "operator": "GT",
                           "otherField": "department.members.salary", "otherAggregate": "AVG"},
                 "sort": [{"field": "fullName"}]}
                """;

        private static final String ABOVE_COMPANY_AVERAGE = """
                {"root": "Personnel", "columns": [{"field": "fullName"}],
                 "where": {"field": "salary", "operator": "GT", "otherField": "salary", "otherAggregate": "AVG"},
                 "sort": [{"field": "fullName"}]}
                """;

        @Test
        @WithMockUser(username = "admin", authorities = {"REPORT_RUN", "SALARY_VIEW", "ALL_DEPARTMENTS"})
        void salaryAboveOwnDepartmentAverage() {
            assertThat(post("/api/reports/run", ABOVE_DEPARTMENT_AVERAGE))
                    .hasStatusOk().bodyJson().extractingPath("$.rows[*][0]").asArray().containsExactly(
                            "Alice Smith", "Carol White", "Dave Miller", "Grace Kim", "Jack Moore", "Mia Allen");
        }

        @Test
        @WithMockUser(username = "admin", authorities = {"REPORT_RUN", "SALARY_VIEW", "ALL_DEPARTMENTS"})
        void salaryAboveCompanyAverage() {
            // company average 68,050: Dave Miller (68,000) just misses it
            assertThat(post("/api/reports/run", ABOVE_COMPANY_AVERAGE))
                    .hasStatusOk().bodyJson().extractingPath("$.rows[*][0]").asArray().containsExactly(
                            "Alice Smith", "Bob Jones", "Carol White", "Diana Prince", "Grace Kim", "Jack Moore", "Mia Allen");
        }

        @Test
        @WithMockUser(username = "carol", authorities = {"REPORT_RUN", "SALARY_VIEW"})
        void theAverageOnlyCoversRowsTheViewerMaySee() {
            // for carol "company" = her department (average 64,400)
            assertThat(post("/api/reports/run", ABOVE_COMPANY_AVERAGE))
                    .hasStatusOk().bodyJson().extractingPath("$.rows[*][0]").asArray()
                    .containsExactly("Carol White", "Dave Miller");
        }

        @Test
        @WithMockUser(username = "admin", authorities = {"REPORT_RUN", "ALL_DEPARTMENTS"})
        void filteredGroupAverage() {
            // hired before the latest hire among the ACTIVE members of the own department (HR: hannah, 2021)
            assertThat(post("/api/reports/run", """
                    {"root": "Personnel", "columns": [{"field": "fullName"}],
                     "where": {"conditions": [
                       {"field": "department.name", "operator": "EQ", "value": "Human Resources"},
                       {"field": "hireDate", "operator": "LT", "otherField": "department.members.hireDate", "otherAggregate": "MAX",
                        "otherWhere": {"field": "active", "operator": "EQ", "value": true}}]},
                     "sort": [{"field": "fullName"}]}
                    """))
                    .hasStatusOk().bodyJson().extractingPath("$.rows[*][0]").asArray()
                    .containsExactly("Alice Smith", "Rachel Green");
        }

        @Test
        @WithMockUser(username = "alice", authorities = "REPORT_RUN")
        void hiddenFieldStaysHiddenInTheAggregate() {
            assertThat(post("/api/reports/run", ABOVE_DEPARTMENT_AVERAGE)).hasStatus(HttpStatus.FORBIDDEN);
        }
    }

    @Nested
    class PreviouslyHiddenFields {

        @Test
        @WithMockUser(username = "alice", authorities = "REPORT_RUN")
        void usernameIdsAndReferenceIdsAreQueryable() {
            assertThat(post("/api/reports/run", """
                    {"root": "Personnel",
                     "columns": [{"field": "id"}, {"field": "username"}, {"field": "department.id"}, {"field": "manager.username"}],
                     "where": {"field": "username", "operator": "IN", "value": ["alice", "hannah"]},
                     "sort": [{"field": "id"}]}
                    """))
                    // alice (HR) does not see her manager ceo (Head Office): row security empties it
                    .hasStatusOk().bodyJson().extractingPath("$.rows").asArray()
                    .containsExactly(row(2, "alice", 2, null), row(3, "hannah", 2, "alice"));
        }

        @Test
        @WithMockUser(username = "alice", authorities = "REPORT_RUN")
        void nationalIdNeedsItsAuthority() {
            assertThat(post("/api/reports/run", """
                    {"root": "Personnel", "columns": [{"field": "nationalId"}]}
                    """))
                    .hasStatus(HttpStatus.FORBIDDEN)
                    .bodyJson().extractingPath("$.detail").isEqualTo("You are not allowed to use field: Personnel / National ID");
        }

        @Test
        @WithMockUser(username = "admin", authorities = {"REPORT_RUN", "ALL_DEPARTMENTS", "NATIONAL_ID_VIEW"})
        void nationalIdWithAuthority() {
            assertThat(post("/api/reports/run", """
                    {"root": "Personnel", "columns": [{"field": "username"}, {"field": "nationalId"}],
                     "where": {"field": "nationalId", "operator": "STARTS_WITH", "value": "1000000000"},
                     "sort": [{"field": "nationalId"}]}
                    """))
                    .hasStatusOk().bodyJson().extractingPath("$.rows[0]").asArray()
                    .containsExactly("ceo", "10000000001");
        }
    }
}
