package com.gucardev.dynamicreportsentity.security;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Browser sessions: login gives an HttpOnly cookie stored in the database (Spring Session JDBC); requests on the
 * session need the CSRF token, Basic-auth requests do not.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SessionApiIntegrationTest {

    @Autowired
    MockMvcTester mvc;

    private MvcTestResult login(String username, String password) {
        return mvc.post().uri("/api/session").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\": \"%s\", \"password\": \"%s\"}".formatted(username, password)).exchange();
    }

    /** Spring Session writes its cookie as a raw Set-Cookie header, so both places are read. */
    private static Cookie cookie(MvcTestResult result, String name) {
        Cookie added = Arrays.stream(result.getResponse().getCookies()).filter(c -> c.getName().equals(name))
                .findFirst().orElse(null);
        if (added != null) {
            return added;
        }
        return result.getResponse().getHeaders("Set-Cookie").stream()
                .filter(header -> header.startsWith(name + "="))
                .map(header -> {
                    String[] parts = header.split(";\\s*");
                    Cookie parsed = new Cookie(name, parts[0].substring(name.length() + 1));
                    parsed.setHttpOnly(Arrays.stream(parts).anyMatch(part -> part.equalsIgnoreCase("HttpOnly")));
                    return parsed;
                })
                .findFirst().orElse(null);
    }

    @Test
    void loginStartsAnHttpOnlySessionThatWorksWithoutThePassword() {
        MvcTestResult login = login("carol", "password");
        System.out.println("DEBUG-HEADERS " + login.getResponse().getHeaderNames().stream()
                .map(n -> n + "=" + login.getResponse().getHeaders(n)).toList() + " session=" + login.getRequest().getSession(false));
        assertThat(login).hasStatus(HttpStatus.CREATED).bodyJson().extractingPath("$.data.username").isEqualTo("carol");
        Cookie session = cookie(login, "REPORTS_SESSION");
        assertThat(session).isNotNull();
        assertThat(session.isHttpOnly()).isTrue();

        assertThat(mvc.get().uri("/api/session").cookie(session).exchange()).hasStatusOk()
                .bodyJson().extractingPath("$.data.authorities").asArray().containsExactly("REPORT_RUN");
        assertThat(mvc.get().uri("/api/reports/catalog").cookie(session).exchange()).hasStatusOk();
    }

    @Test
    void changesOnASessionNeedTheCsrfToken() {
        Cookie session = cookie(login("admin", "password"), "REPORTS_SESSION");
        Cookie csrf = cookie(mvc.get().uri("/api/session").cookie(session).exchange(), "XSRF-TOKEN");
        assertThat(csrf).isNotNull();
        assertThat(csrf.isHttpOnly()).isFalse();   // the UI reads it and sends it back as a header

        String template = """
                {"name": "Csrf %s", "subject": "Hi", "bodyHtml": "<p>Hi</p>"}""";
        assertThat(mvc.post().uri("/api/mail-templates").cookie(session).contentType(MediaType.APPLICATION_JSON)
                .content(template.formatted("missing")).exchange()).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(mvc.post().uri("/api/mail-templates").cookie(session, csrf).header("X-XSRF-TOKEN", csrf.getValue())
                .contentType(MediaType.APPLICATION_JSON).content(template.formatted("sent")).exchange())
                .hasStatus(HttpStatus.CREATED);
        // API clients authenticate on every request and need no token
        assertThat(mvc.post().uri("/api/mail-templates").header("Authorization", "Basic YWRtaW46cGFzc3dvcmQ=")
                .contentType(MediaType.APPLICATION_JSON).content(template.formatted("basic")).exchange())
                .hasStatus(HttpStatus.CREATED);
    }

    @Test
    void logoutEndsTheSessionEverywhere() {
        Cookie session = cookie(login("alice", "password"), "REPORTS_SESSION");
        Cookie csrf = cookie(mvc.get().uri("/api/session").cookie(session).exchange(), "XSRF-TOKEN");

        assertThat(mvc.delete().uri("/api/session").cookie(session, csrf).header("X-XSRF-TOKEN", csrf.getValue())
                .exchange()).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(mvc.get().uri("/api/session").cookie(session).header("X-Requested-With", "XMLHttpRequest")
                .exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void wrongCredentialsDoNotRevealWhichPartWasWrong() throws Exception {
        MvcTestResult wrongPassword = login("alice", "nope");
        MvcTestResult unknownUser = login("nobody", "nope");
        assertThat(wrongPassword).hasStatus(HttpStatus.UNAUTHORIZED).bodyJson().extractingPath("$.code")
                .isEqualTo("INVALID_CREDENTIALS");
        assertThat(unknownUser).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(wrongPassword.getResponse().getContentAsString()).isNotEmpty();
        assertThat(cookie(wrongPassword, "REPORTS_SESSION")).isNull();
    }
}
