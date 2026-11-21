package com.gucardev.dynamicreportsentity.platform.dataset.web;

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

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

/**
 * Saved datasets end to end. Every test uses its own names: the context and its database are shared with the other
 * integration tests.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SavedDatasetApiIntegrationTest {

    private static final RequestPostProcessor ADMIN = as("admin", "PLATFORM_USER", "PLATFORM_ADMIN", "SALARY_VIEW",
            "ALL_DEPARTMENTS");
    /** Row-level security: only Software Development. */
    private static final RequestPostProcessor CAROL = as("carol", "PLATFORM_USER");

    private static final String HEADCOUNT = """
            {"root": "Personnel",
             "columns": [{"field": "department.name"}, {"aggregate": "COUNT"}],
             "sort": [{"field": "department.name"}]}
            """;

    @Autowired
    MockMvcTester mvc;

    private static RequestPostProcessor as(String name, String... authorities) {
        return user(name).authorities(Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList());
    }

    /** Datasets are addressed by their id (a UUID); flows use the name. */
    private String idOf(String name) {
        String body = new String(mvc.get().uri("/api/saved-datasets").with(ADMIN).exchange().getResponse().getContentAsByteArray(), java.nio.charset.StandardCharsets.UTF_8);
        java.util.regex.Matcher match = java.util.regex.Pattern
                .compile("\"id\":\"([0-9a-f-]{36})\",\"name\":\"" + name + "\"").matcher(body);
        assertThat(match.find()).as("dataset " + name).isTrue();
        return match.group(1);
    }

    private MvcTestResult create(RequestPostProcessor user, String name, String definition) {
        return mvc.post().uri("/api/saved-datasets").with(user).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "%s", "label": "Test %s", "definition": %s}""".formatted(name, name, definition))
                .exchange();
    }

    @Test
    void everyoneSavesDatasetsAndOnlyOwnersOrAdminsChangeThem() {
        assertThat(create(CAROL, "s_carol", HEADCOUNT)).hasStatus(HttpStatus.CREATED)
                .bodyJson().extractingPath("$.data.createdBy").isEqualTo("carol");
        assertThat(create(ADMIN, "s_admin", HEADCOUNT)).hasStatus(HttpStatus.CREATED);
        String sCarol = idOf("s_carol");
        String sAdmin = idOf("s_admin");

        assertThat(mvc.get().uri("/api/saved-datasets").with(CAROL).exchange()).hasStatusOk()
                .bodyJson().extractingPath("$.data[*].name").asArray().contains("s_carol", "s_admin");
        assertThat(mvc.get().uri("/api/saved-datasets/" + sCarol).with(ADMIN).exchange()).hasStatusOk()
                .bodyJson().extractingPath("$.data.definition.root").isEqualTo("Personnel");
        // absent parts stay absent (the editor tells "not set" by absence)
        assertThat(mvc.get().uri("/api/saved-datasets/" + sCarol).with(ADMIN).exchange()).bodyText()
                .doesNotContain("\"where\"", "null,\"label\":null");

        assertThat(mvc.put().uri("/api/saved-datasets/" + sAdmin).with(CAROL).contentType(MediaType.APPLICATION_JSON)
                .content("{\"definition\": %s}".formatted(HEADCOUNT)).exchange())
                .hasStatus(HttpStatus.FORBIDDEN).bodyJson().extractingPath("$.code").isEqualTo("NOT_DATASET_OWNER");
        assertThat(mvc.delete().uri("/api/saved-datasets/" + sAdmin).with(CAROL).exchange()).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(mvc.put().uri("/api/saved-datasets/" + sCarol).with(ADMIN).contentType(MediaType.APPLICATION_JSON)
                .content("{\"label\": \"Renamed\", \"definition\": %s}".formatted(HEADCOUNT)).exchange())
                .hasStatusOk().bodyJson().extractingPath("$.data.label").isEqualTo("Renamed");
        assertThat(mvc.delete().uri("/api/saved-datasets/" + sCarol).with(CAROL).exchange())
                .hasStatus(HttpStatus.NO_CONTENT);
        assertThat(mvc.get().uri("/api/saved-datasets/" + sCarol).with(CAROL).exchange())
                .hasStatus(HttpStatus.NOT_FOUND).bodyJson().extractingPath("$.code").isEqualTo("SAVED_DATASET_NOT_FOUND");
    }

    @Test
    void invalidDefinitionsAndNamesAreRejected() {
        assertThat(create(ADMIN, "Bad-Name", HEADCOUNT)).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(create(ADMIN, "s_unknown_field", """
                {"root": "Personnel", "columns": [{"field": "nope"}]}""")).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                .bodyJson().extractingPath("$.code").isEqualTo("INVALID_DATASET_DEFINITION");
        // the definition must be valid for the one saving it: carol may not use salaries
        assertThat(create(CAROL, "s_salary", """
                {"root": "Personnel", "columns": [{"field": "salary"}]}""")).hasStatus(HttpStatus.FORBIDDEN);

        create(ADMIN, "s_twice", HEADCOUNT);
        assertThat(create(ADMIN, "s_twice", HEADCOUNT)).hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.code").isEqualTo("SAVED_DATASET_NAME_TAKEN");
    }
}
