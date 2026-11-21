package com.gucardev.dynamicreportsentity.admin;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

/** The admin (CRUD) APIs of the business modules: validation, versions, filters, sorting, references, deletes. */
@SpringBootTest
@AutoConfigureMockMvc
class AdminCrudIntegrationTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final RequestPostProcessor ADMIN = as("admin", "PLATFORM_USER", "PLATFORM_ADMIN", "SALARY_VIEW",
            "NATIONAL_ID_VIEW", "ALL_DEPARTMENTS");
    /** An admin without the salary and national id permissions. */
    private static final RequestPostProcessor CLERK = as("clerk", "PLATFORM_USER", "PLATFORM_ADMIN");
    private static final RequestPostProcessor CAROL = as("carol", "PLATFORM_USER");

    @Autowired
    MockMvcTester mvc;

    private static RequestPostProcessor as(String name, String... authorities) {
        return user(name).authorities(Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList());
    }

    private static JsonNode json(MvcTestResult result) {
        return JSON.readTree(result.getResponse().getContentAsByteArray());
    }

    private MvcTestResult send(String method, String uri, RequestPostProcessor user, String body, String etag) {
        var request = switch (method) {
            case "POST" -> mvc.post().uri(uri);
            case "PUT" -> mvc.put().uri(uri);
            default -> mvc.delete().uri(uri);
        };
        request = request.with(user);
        if (etag != null) {
            request = request.header(HttpHeaders.IF_MATCH, etag);
        }
        return body == null ? request.exchange() : request.contentType(MediaType.APPLICATION_JSON).content(body).exchange();
    }

    private long id(MvcTestResult result) {
        assertThat(result).hasStatus(HttpStatus.CREATED);
        JsonNode data = json(result).get("data");
        return (data.has("personnel") ? data.get("personnel") : data).get("id").asLong();
    }

    @Test
    void onlyAdminsManageMasterData() {
        assertThat(mvc.get().uri("/api/personnel").with(CAROL).exchange()).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(mvc.get().uri("/api/departments/lookup").with(CAROL).exchange()).hasStatusOk();   // pickers
    }

    @Test
    void departmentsKeepTheirHierarchyAndCannotBeDeletedWhileUsed() throws Exception {
        String suffix = Long.toString(System.nanoTime());
        long parent = id(send("POST", "/api/departments", ADMIN, "{\"name\": \"Ar-Ge " + suffix + "\", \"costCenter\": \"CC-9\"}", null));
        long child = id(send("POST", "/api/departments", ADMIN,
                "{\"name\": \"Mobil " + suffix + "\", \"parentId\": " + parent + "}", null));

        MvcTestResult current = mvc.get().uri("/api/departments/" + parent).with(ADMIN).exchange();
        String etag = current.getResponse().getHeader(HttpHeaders.ETAG);
        assertThat(json(current).get("data").get("subDepartments").asInt()).isEqualTo(1);
        MvcTestResult cycle = send("PUT", "/api/departments/" + parent, ADMIN,
                "{\"name\": \"Ar-Ge " + suffix + "\", \"parentId\": " + child + "}", etag);
        assertThat(cycle).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(cycle.getResponse().getContentAsString()).contains("HIERARCHY_CYCLE");
        assertThat(send("PUT", "/api/departments/" + parent, ADMIN, "{\"name\": \"x\"}", "\"99\""))
                .hasStatus(HttpStatus.PRECONDITION_FAILED);

        long person = id(send("POST", "/api/personnel", ADMIN, """
                {"fullName": "Deniz Test", "email": "deniz.%s@example.com", "departmentId": %d, "active": true}"""
                .formatted(suffix, child), null));
        MvcTestResult used = send("DELETE", "/api/departments/" + child, ADMIN, null, null);
        assertThat(used).hasStatus(HttpStatus.CONFLICT);
        assertThat(used.getResponse().getContentAsString()).contains("1 çalışan");
        assertThat(send("DELETE", "/api/departments/" + parent, ADMIN, null, null).getResponse().getContentAsString())
                .contains("1 alt departman");
        assertThat(json(mvc.get().uri("/api/departments?q=mobil " + suffix).with(ADMIN).exchange()).get("data").get(0)
                .get("usage").get("çalışan").asInt()).isEqualTo(1);

        assertThat(send("DELETE", "/api/personnel/" + person, ADMIN, null, null)).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(send("DELETE", "/api/departments/" + child, ADMIN, null, null)).hasStatus(HttpStatus.NO_CONTENT);
    }

    @Test
    void personnelValidatesReferencesAndHidesSalaryWithoutPermission() throws Exception {
        long department = json(mvc.get().uri("/api/departments/lookup").with(ADMIN).exchange()).get("data").get(0).get("id").asLong();
        MvcTestResult invalid = send("POST", "/api/personnel", ADMIN, """
                {"fullName": "", "email": "not-mail", "departmentId": 999999, "managerId": 999999, "nationalId": "12"}""", null);
        assertThat(invalid).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(invalid.getResponse().getContentAsString()).contains("INVALID_EMAIL").contains("DEPARTMENT_NOT_FOUND")
                .contains("PERSONNEL_NOT_FOUND").contains("nationalId");

        String suffix = Long.toString(System.nanoTime());
        MvcTestResult created = send("POST", "/api/personnel", ADMIN, """
                {"username": "u%s", "fullName": "Selin Kaya %s", "email": "selin@example.com", "hireDate": "2025-03-01",
                 "departmentId": %d, "salary": 85000, "nationalId": "12345678901",
                 "profile": {"city": "İzmir", "bloodType": "A+"}}""".formatted(suffix, suffix, department), null);
        long person = id(created);
        assertThat(json(created).get("data").get("profile").get("city").asString()).isEqualTo("İzmir");

        JsonNode asClerk = json(mvc.get().uri("/api/personnel/" + person).with(CLERK).exchange()).get("data");
        assertThat(asClerk.get("personnel").get("salary").isNull()).isTrue();
        assertThat(asClerk.get("canSeeSalary").asBoolean()).isFalse();
        // a clerk saving the person does not wipe the salary they cannot see
        String etag = mvc.get().uri("/api/personnel/" + person).with(CLERK).exchange().getResponse().getHeader(HttpHeaders.ETAG);
        assertThat(send("PUT", "/api/personnel/" + person, CLERK, """
                {"username": "u%s", "fullName": "Selin Kaya %s", "departmentId": %d, "active": false}"""
                .formatted(suffix, suffix, department), etag)).hasStatusOk();
        JsonNode asAdmin = json(mvc.get().uri("/api/personnel/" + person).with(ADMIN).exchange()).get("data").get("personnel");
        assertThat(asAdmin.get("salary").asInt()).isEqualTo(85000);
        assertThat(asAdmin.get("active").asBoolean()).isFalse();
        assertThat(mvc.get().uri("/api/personnel?sort=-salary").with(CLERK).exchange()).hasStatus(HttpStatus.BAD_REQUEST);

        JsonNode filtered = json(mvc.get().uri("/api/personnel?q=selin kaya " + suffix + "&active=false&departmentId=" + department)
                .with(ADMIN).exchange());
        assertThat(filtered.get("data")).hasSize(1);
        assertThat(filtered.get("meta").get("totalItems").asInt()).isEqualTo(1);

        JsonNode skills = json(mvc.get().uri("/api/skills/lookup").with(ADMIN).exchange()).get("data");
        long skill = skills.get(0).get("id").asLong();
        assertThat(send("PUT", "/api/personnel/" + person + "/skills", ADMIN, "{\"skillIds\": [" + skill + "]}", null)).hasStatusOk();
        assertThat(send("DELETE", "/api/skills/" + skill, ADMIN, null, null)).hasStatus(HttpStatus.CONFLICT);
    }

    @Test
    void projectsTrainingsAndRecordsFollowTheirPeople() throws Exception {
        String suffix = Long.toString(System.nanoTime() % 1_000_000);
        long department = json(mvc.get().uri("/api/departments/lookup").with(ADMIN).exchange()).get("data").get(0).get("id").asLong();
        long person = id(send("POST", "/api/personnel", ADMIN,
                "{\"fullName\": \"Can Test\", \"departmentId\": " + department + "}", null));

        long project = id(send("POST", "/api/projects", ADMIN, """
                {"code": "t-%s", "name": "Test projesi", "status": "ACTIVE", "budget": 1000,
                 "startDate": "2026-01-01", "endDate": "2026-06-30", "departmentId": %d}""".formatted(suffix, department), null));
        assertThat(json(mvc.get().uri("/api/projects/" + project).with(ADMIN).exchange()).get("data").get("code").asString())
                .isEqualTo("T-" + suffix);
        assertThat(send("POST", "/api/projects", ADMIN, """
                {"code": "T-%s", "name": "x", "status": "DONE", "startDate": "2026-02-01", "endDate": "2026-01-01"}"""
                .formatted(suffix), null).getResponse().getContentAsString())
                .contains("CODE_TAKEN").contains("status").contains("endDate");
        assertThat(send("POST", "/api/projects/" + project + "/assignments", ADMIN,
                "{\"personnelId\": " + person + ", \"role\": \"Analist\", \"allocationPercent\": 50}", null))
                .hasStatus(HttpStatus.CREATED);
        assertThat(send("POST", "/api/projects/" + project + "/assignments", ADMIN,
                "{\"personnelId\": " + person + ", \"allocationPercent\": 120}", null).getResponse().getContentAsString())
                .contains("ALREADY_ASSIGNED").contains("allocationPercent");
        JsonNode listed = json(mvc.get().uri("/api/projects?q=t-" + suffix + "&status=ACTIVE").with(ADMIN).exchange()).get("data").get(0);
        assertThat(listed.get("assignments").asInt()).isEqualTo(1);
        assertThat(listed.get("allocation").asInt()).isEqualTo(50);

        long training = id(send("POST", "/api/trainings", ADMIN,
                "{\"name\": \"İSG " + suffix + "\", \"category\": \"Safety\", \"validityMonths\": 12, \"mandatory\": true}", null));
        MvcTestResult record = send("POST", "/api/training-records", ADMIN, """
                {"personnelId": %d, "trainingId": %d, "completedOn": "2025-01-10", "score": 90, "status": "PASSED"}"""
                .formatted(person, training), null);
        assertThat(record).hasStatus(HttpStatus.CREATED);
        assertThat(json(record).get("data").get("nextRenewalDate").asString()).isEqualTo("2026-01-10");   // + 12 months
        JsonNode overdue = json(mvc.get().uri("/api/training-records?trainingId=" + training + "&due=overdue").with(ADMIN).exchange());
        assertThat(overdue.get("data")).hasSize(LocalDate.now().isAfter(LocalDate.of(2026, 1, 10)) ? 1 : 0);
        assertThat(overdue.get("data").get(0).get("personnel").get("name").asString()).isEqualTo("Can Test");
        assertThat(send("DELETE", "/api/trainings/" + training, ADMIN, null, null)).hasStatus(HttpStatus.CONFLICT);

        // deleting the person removes their records and assignments (after commit, through an event)
        assertThat(send("DELETE", "/api/personnel/" + person, ADMIN, null, null)).hasStatus(HttpStatus.NO_CONTENT);
        Instant end = Instant.now().plus(Duration.ofSeconds(10));
        while (json(mvc.get().uri("/api/training-records?trainingId=" + training).with(ADMIN).exchange()).get("data").size() > 0) {
            assertThat(Instant.now()).isBefore(end);
            Thread.sleep(100);
        }
        assertThat(json(mvc.get().uri("/api/projects/" + project + "/assignments").with(ADMIN).exchange()).get("data")).isEmpty();
        assertThat(send("DELETE", "/api/trainings/" + training, ADMIN, null, null)).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(send("DELETE", "/api/projects/" + project, ADMIN, null, null)).hasStatus(HttpStatus.NO_CONTENT);
    }
}
