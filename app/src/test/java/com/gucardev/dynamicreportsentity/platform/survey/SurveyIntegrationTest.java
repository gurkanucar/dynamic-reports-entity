package com.gucardev.dynamicreportsentity.platform.survey;

import com.gucardev.dynamicreportsentity.platform.flow.RecordingMailSender;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
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
import java.util.Arrays;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

/**
 * Surveys end to end: designing (checks), answering (visibility, required, types, rules, one response per user),
 * results (summary, Excel) and the flow event a response starts.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SurveyIntegrationTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final RequestPostProcessor CAROL = as("carol", "PLATFORM_USER");
    private static final RequestPostProcessor ALICE = as("alice", "PLATFORM_USER");
    private static final RequestPostProcessor BOB = as("bob", "PLATFORM_USER", "SALARY_VIEW");

    /** A leave request: dates with a rule, a follow-up shown only for "other", a choice and a rating. */
    private static final String LEAVE = """
            {"submitLabel": "Gönder", "successMessage": "Talebiniz alındı.",
             "fields": [
               {"id": "info", "type": "section", "label": "Talep"},
               {"id": "kind", "type": "singleChoice", "label": "İzin türü", "required": true,
                "options": [{"id": "annual", "label": "Yıllık"}, {"id": "sick", "label": "Hastalık"},
                            {"id": "other", "label": "Diğer"}]},
               {"id": "otherReason", "type": "shortText", "label": "Açıklama", "required": true,
                "visibleWhen": {"==": [{"var": "kind"}, "other"]}},
               {"id": "startDate", "type": "date", "label": "Başlangıç", "required": true, "width": "half"},
               {"id": "endDate", "type": "date", "label": "Bitiş", "required": true, "width": "half"},
               {"id": "days", "type": "number", "label": "Gün", "min": 1, "max": 30},
               {"id": "happy", "type": "rating", "label": "Süreçten memnun musunuz?"}
             ],
             "rules": [{"id": "endAfterStart", "message": "Bitiş tarihi başlangıçtan önce olamaz.",
                        "logic": {">=": [{"var": "endDate"}, {"var": "startDate"}]}}]}""";

    @Autowired
    MockMvcTester mvc;

    private static RequestPostProcessor as(String name, String... authorities) {
        return user(name).authorities(Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList());
    }

    @BeforeEach
    void setUp() {
        RecordingMailSender.reset();
    }

    private static JsonNode json(MvcTestResult result) {
        return JSON.readTree(result.getResponse().getContentAsByteArray());
    }

    private MvcTestResult post(String uri, RequestPostProcessor user, String body) {
        return mvc.post().uri(uri).with(user).contentType(MediaType.APPLICATION_JSON).content(body).exchange();
    }

    private String survey(String title, String status, String definition, boolean oneResponse) {
        return """
                {"title": "%s", "status": "%s", "definition": %s, "oneResponsePerUser": %s}"""
                .formatted(title, status, definition, oneResponse);
    }

    private String create(String body) {
        MvcTestResult result = post("/api/surveys", CAROL, body);
        assertThat(result).hasStatus(HttpStatus.CREATED);
        return json(result).get("data").get("id").asString();
    }

    private MvcTestResult answer(String id, RequestPostProcessor user, Map<String, Object> answers) {
        return post("/api/surveys/" + id + "/responses", user, JSON.writeValueAsString(Map.of("answers", answers)));
    }

    @Test
    void invalidDefinitionsAreRefusedWithTheirProblems() throws Exception {
        MvcTestResult result = post("/api/surveys", CAROL, survey("Bozuk", "draft", """
                {"fields": [
                  {"id": "a", "type": "singleChoice", "label": "Tek seçenek", "options": [{"id": "x", "label": "X"}]},
                  {"id": "a", "type": "magic", "label": "?"},
                  {"id": "b", "type": "shortText", "label": "B", "visibleWhen": {"==": [{"var": "later"}, 1]}},
                  {"id": "later", "type": "number", "label": "Sonra"}
                ],
                 "rules": [{"id": "r", "message": "m", "logic": {"eval": ["x"]}}]}""", false));
        assertThat(result).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(result.getResponse().getContentAsString())
                .contains("definition.fields[0].options").contains("DUPLICATE_ID").contains("UNKNOWN_TYPE")
                .contains("UNKNOWN_FIELD_REFERENCE").contains("INVALID_EXPRESSION");
    }

    @Test
    void responsesAreCheckedLikeTheFormAndCounted() throws Exception {
        String id = create(survey("İzin talebi", "draft", LEAVE, true));

        // a draft: only the owner can open it, nobody can answer it
        assertThat(mvc.get().uri("/api/surveys/" + id + "/form").with(ALICE).exchange()).hasStatus(HttpStatus.NOT_FOUND);
        JsonNode preview = json(mvc.get().uri("/api/surveys/" + id + "/form").with(CAROL).exchange()).get("data");
        assertThat(preview.get("preview").asBoolean()).isTrue();
        assertThat(preview.get("open").asBoolean()).isFalse();

        // publish (If-Match)
        MvcTestResult current = mvc.get().uri("/api/surveys/" + id).with(CAROL).exchange();
        MvcTestResult published = mvc.put().uri("/api/surveys/" + id).with(CAROL)
                .header(HttpHeaders.IF_MATCH, current.getResponse().getHeader(HttpHeaders.ETAG))
                .contentType(MediaType.APPLICATION_JSON).content(survey("İzin talebi", "published", LEAVE, true))
                .exchange();
        assertThat(published).hasStatus(HttpStatus.OK);
        assertThat(mvc.get().uri("/api/surveys/" + id).with(ALICE).exchange()).hasStatus(HttpStatus.NOT_FOUND);
        JsonNode form = json(mvc.get().uri("/api/surveys/" + id + "/form").with(ALICE).exchange()).get("data");
        assertThat(form.get("open").asBoolean()).isTrue();
        assertThat(form.get("definition").get("rules").get(0).has("logic")).isFalse();   // rules stay on the server

        // the follow-up is required only when shown; the rule runs over all answers
        MvcTestResult missing = answer(id, ALICE, Map.of("kind", "other", "startDate", "2026-12-01",
                "endDate", "2026-12-03"));
        assertThat(missing).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(missing.getResponse().getContentAsString()).contains("answers.otherReason");
        MvcTestResult backwards = answer(id, ALICE, Map.of("kind", "annual", "startDate", "2026-12-05",
                "endDate", "2026-12-01"));
        assertThat(backwards).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(backwards.getResponse().getContentAsString()).contains("rules.endAfterStart")
                .contains("Bitiş tarihi başlangıçtan önce olamaz.");
        MvcTestResult outOfRange = answer(id, ALICE, Map.of("kind", "annual", "startDate", "2026-12-01",
                "endDate", "2026-12-02", "days", 45, "happy", 9));
        assertThat(outOfRange.getResponse().getContentAsString()).contains("answers.days").contains("answers.happy");

        // accepted: the hidden follow-up is dropped
        MvcTestResult accepted = answer(id, ALICE, Map.of("kind", "annual", "otherReason", "gizli",
                "startDate", "2026-12-01", "endDate", "2026-12-03", "days", "3", "happy", 4));
        assertThat(accepted).hasStatus(HttpStatus.CREATED);
        assertThat(json(accepted).get("data").get("successMessage").asString()).isEqualTo("Talebiniz alındı.");
        assertThat(answer(id, ALICE, Map.of("kind", "sick", "startDate", "2026-12-01", "endDate", "2026-12-01")))
                .hasStatus(HttpStatus.CONFLICT);
        assertThat(answer(id, BOB, Map.of("kind", "sick", "startDate", "2026-12-01", "endDate", "2026-12-01",
                "happy", 4))).hasStatus(HttpStatus.CREATED);
        assertThat(json(mvc.get().uri("/api/surveys/" + id + "/form").with(ALICE).exchange())
                .get("data").get("closedReason").asString()).isEqualTo("answered");

        JsonNode responses = json(mvc.get().uri("/api/surveys/" + id + "/responses").with(CAROL).exchange());
        assertThat(responses.get("data")).hasSize(2);
        JsonNode alice = responses.get("data").get(1);
        assertThat(alice.get("respondent").asString()).isEqualTo("alice");
        assertThat(alice.get("answers").has("otherReason")).isFalse();
        assertThat(alice.get("answers").get("days").asInt()).isEqualTo(3);
        assertThat(alice.get("display").get("kind").asString()).isEqualTo("Yıllık");

        JsonNode summary = json(mvc.get().uri("/api/surveys/" + id + "/summary").with(CAROL).exchange()).get("data");
        assertThat(summary.get("total").asInt()).isEqualTo(2);
        JsonNode kind = summary.get("fields").get(0);
        assertThat(kind.get("fieldId").asString()).isEqualTo("kind");
        assertThat(kind.get("counts").get(0).get("count").asInt()).isEqualTo(1);   // annual
        assertThat(kind.get("counts").get(1).get("count").asInt()).isEqualTo(1);   // sick
        assertThat(summary.get("fields").get(5).get("average").asDouble()).isEqualTo(4.0);

        MvcTestResult export = mvc.get().uri("/api/surveys/" + id + "/responses/export").with(CAROL).exchange();
        assertThat(export).hasStatusOk();
        try (var workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook(
                new java.io.ByteArrayInputStream(export.getResponse().getContentAsByteArray()))) {
            var sheet = workbook.getSheetAt(0);
            assertThat(sheet.getRow(0).getCell(2).getStringCellValue()).isEqualTo("Gönderen");
            assertThat(sheet.getRow(0).getCell(3).getStringCellValue()).isEqualTo("İzin türü");
            assertThat(sheet.getRow(1).getCell(2).getStringCellValue()).isEqualTo("alice");
            assertThat(sheet.getRow(1).getCell(3).getStringCellValue()).isEqualTo("Yıllık");
            assertThat(sheet.getRow(1).getCell(7).getNumericCellValue()).isEqualTo(3.0);   // days, a number
            assertThat(sheet.getLastRowNum()).isEqualTo(2);
        }

        JsonNode open = json(mvc.get().uri("/api/surveys?scope=open").with(ALICE).exchange()).get("data");
        assertThat(open.findValues("id").stream().map(JsonNode::asString)).contains(id);
    }

    @Test
    void aResponseStartsTheFlowsListeningOnTheSurvey() throws Exception {
        String id = create(survey("Ekipman talebi", "published", """
                {"fields": [
                  {"id": "item", "type": "dropdown", "label": "Ne lazım?", "required": true,
                   "options": [{"id": "laptop", "label": "Dizüstü bilgisayar"}, {"id": "phone", "label": "Telefon"}]},
                  {"id": "urgent", "type": "yesNo", "label": "Acil mi?"}
                ]}""", false));
        MvcTestResult template = post("/api/mail-templates", CAROL, JSON.writeValueAsString(Map.of(
                "name", "Ekipman " + Instant.now().toEpochMilli(),
                "subject", "Yeni talep: {{trigger.text.item}}",
                "bodyHtml", "<p>{{trigger.respondent}} istedi: {{trigger.text.item}} (acil: {{trigger.text.urgent}})</p>")));
        assertThat(template).hasStatus(HttpStatus.CREATED);
        String templateId = json(template).get("data").get("id").asString();
        MvcTestResult flow = post("/api/flows", CAROL, """
                {"name": "Ekipman talebi akışı %d",
                 "steps": [{"type": "mail", "key": "mail", "templateId": "%s", "to": ["it@example.com"]}]}"""
                .formatted(Instant.now().toEpochMilli(), templateId));
        assertThat(flow).hasStatus(HttpStatus.CREATED);
        String flowId = json(flow).get("data").get("id").asString();
        assertThat(post("/api/flows/" + flowId + "/triggers", CAROL, """
                {"type": "event", "enabled": true, "topic": "survey:%s",
                 "filter": {"==": [{"var": "trigger.answers.urgent"}, true]}}""".formatted(id)))
                .hasStatus(HttpStatus.CREATED);
        assertThat(post("/api/flows/" + flowId + "/triggers", CAROL, """
                {"type": "event", "enabled": true, "topic": "survey"}""")).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);

        assertThat(answer(id, ALICE, Map.of("item", "phone", "urgent", false))).hasStatus(HttpStatus.CREATED);
        assertThat(answer(id, ALICE, Map.of("item", "laptop", "urgent", true))).hasStatus(HttpStatus.CREATED);

        await(() -> RecordingMailSender.SENT.size() == 1, Duration.ofSeconds(20));
        MimeMessage mail = RecordingMailSender.SENT.getFirst();
        assertThat(subject(mail)).isEqualTo("Yeni talep: Dizüstü bilgisayar");
        JsonNode runs = json(mvc.get().uri("/api/flows/" + flowId + "/runs").with(CAROL).exchange()).get("data");
        assertThat(runs).hasSize(1);   // the filter let only the urgent one through
        assertThat(runs.get(0).get("trigger").asString()).isEqualTo("event");
    }

    @Test
    void theOwnerFollowsUpResponsesWithStatusPriorityFieldsAndNotes() throws Exception {
        String id = create("""
                {"title": "Şikayet formu", "status": "published", "oneResponsePerUser": false,
                 "settings": {"tracking": {"status": true, "priority": true, "followUpDate": true, "assignee": true,
                   "fields": [{"id": "ticket", "label": "Kayıt no", "type": "text"},
                              {"id": "area", "label": "Alan", "type": "choice",
                               "options": [{"id": "it", "label": "BT"}, {"id": "hr", "label": "İK"}]}]}},
                 "definition": {"fields": [{"id": "topic", "type": "shortText", "label": "Konu", "required": true}]}}""");
        String first = json(answer(id, ALICE, Map.of("topic", "Yazıcı bozuk"))).get("data").get("responseId").asString();
        answer(id, BOB, Map.of("topic", "Maaş bordrosu yanlış"));

        MvcTestResult patched = mvc.patch().uri("/api/surveys/" + id + "/responses/" + first).with(CAROL)
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"status": "in_progress", "priority": "high", "followUpAt": "2026-12-01", "assignee": "bob",
                         "tracking": {"ticket": "T-17", "area": "it"}}""").exchange();
        assertThat(patched).hasStatusOk();
        JsonNode view = json(patched).get("data");
        assertThat(view.get("status").asString()).isEqualTo("in_progress");
        assertThat(view.get("priority").asString()).isEqualTo("high");
        assertThat(view.get("tracking").get("area").asString()).isEqualTo("it");
        assertThat(mvc.patch().uri("/api/surveys/" + id + "/responses/" + first).with(CAROL)
                .contentType(MediaType.APPLICATION_JSON).content("{\"tracking\": {\"area\": \"sales\"}}").exchange())
                .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(mvc.patch().uri("/api/surveys/" + id + "/responses/" + first).with(ALICE)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\": \"resolved\"}").exchange())
                .hasStatus(HttpStatus.NOT_FOUND);   // only the owner follows up

        JsonNode filtered = json(mvc.get().uri("/api/surveys/" + id + "/responses?status=in_progress").with(CAROL).exchange());
        assertThat(filtered.get("data")).hasSize(1);
        assertThat(json(mvc.get().uri("/api/surveys/" + id + "/responses?q=bordro").with(CAROL).exchange()).get("data"))
                .hasSize(1);
        JsonNode sorted = json(mvc.get().uri("/api/surveys/" + id + "/responses?sort=-priority").with(CAROL).exchange());
        assertThat(sorted.get("data").get(0).get("id").asString()).isEqualTo(first);
        assertThat(mvc.get().uri("/api/surveys/" + id + "/responses?sort=answersJson").with(CAROL).exchange())
                .hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(json(mvc.get().uri("/api/surveys/" + id).with(CAROL).exchange()).get("data")
                .get("followUpCounts").get("in_progress").asInt()).isEqualTo(1);

        MvcTestResult note = post("/api/surveys/" + id + "/responses/" + first + "/notes", CAROL,
                "{\"text\": \"Teknisyen yönlendirildi.\"}");
        assertThat(note).hasStatus(HttpStatus.CREATED);
        String noteId = json(note).get("data").get("id").asString();
        assertThat(json(mvc.get().uri("/api/surveys/" + id + "/responses/" + first + "/notes").with(CAROL).exchange())
                .get("data").get(0).get("author").asString()).isEqualTo("carol");
        assertThat(json(mvc.get().uri("/api/surveys/" + id + "/responses?status=in_progress").with(CAROL).exchange())
                .get("data").get(0).get("notes").asInt()).isEqualTo(1);
        assertThat(mvc.delete().uri("/api/surveys/" + id + "/responses/" + first + "/notes/" + noteId).with(CAROL)
                .exchange()).hasStatus(HttpStatus.NO_CONTENT);
    }

    @Test
    void respondentsSeeTheCountedResultsAfterAnswering() throws Exception {
        String poll = """
                {"title": "Öğle yemeği", "status": "published", "oneResponsePerUser": true,
                 "settings": {"results": "respondents"},
                 "definition": {"fields": [
                   {"id": "place", "type": "singleChoice", "label": "Nerede?", "showInResults": true,
                    "options": [{"id": "a", "label": "Kebapçı"}, {"id": "b", "label": "Balıkçı"}]},
                   {"id": "why", "type": "shortText", "label": "Neden?"}]}}""";
        String id = create(poll);
        assertThat(mvc.get().uri("/api/surveys/" + id + "/results").with(ALICE).exchange()).hasStatus(HttpStatus.FORBIDDEN);
        answer(id, ALICE, Map.of("place", "b", "why", "Deniz kenarı"));
        JsonNode results = json(mvc.get().uri("/api/surveys/" + id + "/results").with(ALICE).exchange()).get("data");
        assertThat(results.get("fields")).hasSize(1);   // only the question marked for results; no text answers
        assertThat(results.get("fields").get(0).get("counts").get(1).get("count").asInt()).isEqualTo(1);

        MvcTestResult personal = post("/api/surveys", CAROL, poll.replace("\"Neden?\"}", "\"Neden?\", \"showInResults\": true}"));
        assertThat(personal).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
        MvcTestResult anonymous = post("/api/surveys", CAROL, poll.replace("\"oneResponsePerUser\": true",
                "\"oneResponsePerUser\": false, \"anonymous\": true"));
        assertThat(anonymous.getResponse().getContentAsString()).contains("RESULTS_NEED_RESPONDENT");
    }

    @Test
    void aSurveyStepMailsALinkAndWaitsForTheAnswer() throws Exception {
        String id = create(survey("Ekipman listesi", "published", """
                {"fields": [{"id": "items", "type": "multipleChoice", "label": "Neler lazım?", "required": true,
                  "options": [{"id": "laptop", "label": "Dizüstü"}, {"id": "phone", "label": "Telefon"}]}]}""", true));
        MvcTestResult template = post("/api/mail-templates", CAROL, JSON.writeValueAsString(Map.of(
                "name", "Ekipman siparişi " + Instant.now().toEpochMilli(), "subject", "Sipariş: {{steps.ask.text.items}}",
                "bodyHtml", "<p>{{steps.ask.respondent}}: {{steps.ask.text.items}}</p>")));
        String templateId = json(template).get("data").get("id").asString();
        String flowId = json(post("/api/flows", CAROL, """
                {"name": "Yeni çalışan ekipmanı %d",
                 "steps": [{"type": "survey", "key": "ask", "surveyId": "%s", "to": ["yonetici@example.com"],
                            "message": "Yeni çalışan için ekipmanları seçin."},
                           {"type": "mail", "key": "order", "templateId": "%s", "to": ["it@example.com"]}]}"""
                .formatted(Instant.now().toEpochMilli(), id, templateId))).get("data").get("id").asString();
        assertThat(mvc.post().uri("/api/flows/" + flowId + "/runs").with(CAROL).exchange()).hasStatus(HttpStatus.ACCEPTED);

        await(() -> RecordingMailSender.SENT.size() == 1, Duration.ofSeconds(20));
        String body = String.valueOf(RecordingMailSender.SENT.getFirst().getContent());
        java.util.regex.Matcher link = java.util.regex.Pattern.compile("invitation=([A-Za-z0-9_-]+)").matcher(body);
        assertThat(link.find()).isTrue();
        String token = link.group(1);
        JsonNode form = json(mvc.get().uri("/api/surveys/" + id + "/form?invitation=" + token).with(ALICE).exchange());
        assertThat(form.get("data").get("open").asBoolean()).isTrue();

        MvcTestResult answered = post("/api/surveys/" + id + "/responses", ALICE, JSON.writeValueAsString(Map.of(
                "answers", Map.of("items", java.util.List.of("laptop", "phone")), "invitation", token)));
        assertThat(answered).hasStatus(HttpStatus.CREATED);
        await(() -> RecordingMailSender.SENT.size() == 2, Duration.ofSeconds(20));
        assertThat(subject(RecordingMailSender.SENT.get(1))).isEqualTo("Sipariş: Dizüstü, Telefon");
        assertThat(post("/api/surveys/" + id + "/responses", ALICE, JSON.writeValueAsString(Map.of(
                "answers", Map.of("items", java.util.List.of("phone")), "invitation", token))))
                .hasStatus(HttpStatus.CONFLICT);   // single use
        // the invitation's response does not use up alice's own "one response per user"
        assertThat(answer(id, ALICE, Map.of("items", java.util.List.of("phone")))).hasStatus(HttpStatus.CREATED);
    }

    private static String subject(MimeMessage mail) {
        try {
            return mail.getSubject();
        } catch (jakarta.mail.MessagingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void await(java.util.function.BooleanSupplier condition, Duration timeout) {
        Instant end = Instant.now().plus(timeout);
        while (!condition.getAsBoolean()) {
            if (Instant.now().isAfter(end)) {
                throw new AssertionError("Condition not met within " + timeout);
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError(e);
            }
        }
    }
}
