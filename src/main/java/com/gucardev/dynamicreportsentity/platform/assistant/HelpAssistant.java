package com.gucardev.dynamicreportsentity.platform.assistant;

import com.gucardev.dynamicreportsentity.platform.identity.Actor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * The help chat: answers questions about the platform's pages from a written guide ({@code assistant/help-guide.md})
 * and the page the user is on, and hands building work (a dataset, a flow, a survey, a mail template) over to the
 * assistant made for it. It sees no data and no records, only the guide, the page path and whether the user is an
 * admin. The draft is {@code {links: [{label, path}], handoff: {assistant, path, request} | null}}; links are checked
 * against the pages that exist (admin pages only for admins), so the model cannot send anyone to a made-up page.
 */
@Component
class HelpAssistant implements Drafter {

    /** Pages a link may point at. */
    private static final List<Pattern> PAGES = List.of(
            Pattern.compile("/"),
            Pattern.compile("/(datasets|datasets/new|flows|flows/new|runs|approvals|approval-groups|templates|surveys|surveys/new)"),
            Pattern.compile("/datasets/[a-z0-9_]{1,80}"),
            Pattern.compile("/(flows|surveys)/\\d{1,12}(/edit)?"));
    private static final Pattern ADMIN_PAGES =
            Pattern.compile("/admin/(personnel|departments|projects|trainings|training-records|skills)");
    /** Where each specialist assistant lives; {@code ?ask=} fills its input with the request. */
    private static final Map<String, String> HANDOFFS = Map.of(
            "dataset", "/datasets/new",
            "flow", "/flows/new",
            "survey", "/surveys/new",
            "mail-template", "/templates?new=1");
    private static final Set<String> ROLES = Set.of("PLATFORM_ADMIN");
    private static final String PROMPT = """
            You are the help desk of the platform described below. Answer the user's questions about its pages:
            where something is, what a page or button does, how to do a task step by step, what an error means.
            - Answer in the user's language (usually Turkish), briefly: a few sentences or a short numbered list.
              Use the page and button names as the UI shows them (Turkish).
            - Only describe what the guide says exists. If something is not in the guide, say you do not know and
              suggest the closest page; never invent pages, buttons or features.
            - You cannot see the user's data or records and cannot change anything. For questions about their data
              ("kaç kişi var?") point to building a dataset.
            - Building or changing something (a dataset/report, a flow/automation, a survey/form, a mail template)
              is the job of the specialist assistants: explain in one sentence and set `handoff` with the assistant
              and the user's request rewritten as a clear instruction for it.
            - Admin pages (/admin/...) exist only for admins; the user is {{ADMIN}}.
            - The user is on {{PAGE}}. "Bu sayfa" means that page.
            Always reply by calling `answer`, with links to the pages you mention (paths from the guide).

            """;
    private static final List<Map<String, Object>> TOOLS = List.of(ToolLoop.function("answer",
            "Your reply to the user, with links to the pages it mentions and an optional hand-over.",
            Map.of("type", "object", "required", List.of("message"), "properties", Map.of(
                    "message", Map.of("type", "string", "description", "The answer, plain text (may use **bold** and lists)"),
                    "links", Map.of("type", "array", "maxItems", 4, "items", Map.of("type", "object",
                            "required", List.of("label", "path"), "properties", Map.of(
                                    "label", Map.of("type", "string"),
                                    "path", Map.of("type", "string", "description", "A page path from the guide, e.g. /runs")))),
                    "handoff", Map.of("type", "object", "required", List.of("assistant", "request"), "properties", Map.of(
                            "assistant", Map.of("type", "string", "enum", List.copyOf(HANDOFFS.keySet())),
                            "request", Map.of("type", "string", "description",
                                    "What to ask that assistant, in the user's language")))))));

    private final ChatClient chat;
    private final AssistantProperties properties;
    private final String guide;

    HelpAssistant(ChatClient chat, AssistantProperties properties) {
        this.chat = chat;
        this.properties = properties;
        try {
            this.guide = new ClassPathResource("assistant/help-guide.md").getContentAsString(UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public String kind() {
        return "help";
    }

    /** @param current {@code {path, title}} of the page the user is on */
    @Override
    public SuggestionResult draft(List<ChatMessage> conversation, Object current, Actor viewer) {
        boolean admin = viewer.authorities().stream().anyMatch(ROLES::contains);
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(ToolLoop.message("system", PROMPT
                .replace("{{ADMIN}}", admin ? "an admin" : "NOT an admin")
                .replace("{{PAGE}}", page(current)) + guide));
        conversation.forEach(turn -> messages.add(ToolLoop.message(turn.role(), turn.content())));
        return ToolLoop.run(chat, properties, messages, TOOLS, (name, arguments, said, iteration, model) -> {
            if (!"answer".equals(name)) {
                return new ToolLoop.ToolAnswer(Map.of("error", "Reply with the answer tool."));
            }
            String message = ToolLoop.text(arguments, "message");
            if (message == null || message.isBlank()) {
                return new ToolLoop.ToolAnswer(Map.of("error", "message is required."));
            }
            Map<String, Object> draft = new LinkedHashMap<>();
            draft.put("links", links(arguments == null ? null : arguments.get("links"), admin));
            draft.put("handoff", handoff(arguments == null ? null : arguments.get("handoff")));
            return new ToolLoop.Done(new SuggestionResult(message, null, null, null, List.of(), draft, iteration, model));
        }, "Bunu anlayamadım; hangi sayfa ya da işlem hakkında yardım istediğinizi biraz açar mısınız?",
                "The help assistant could not answer.");
    }

    /** "/flows/12 (Akış detayı)": what the client said, cut short and stripped of anything but the path and title. */
    static String page(Object current) {
        if (!(current instanceof Map<?, ?> map)) {
            return "an unknown page";
        }
        String path = map.get("path") instanceof String text ? text.replaceAll("[^A-Za-z0-9/_?=&.-]", "") : "";
        String title = map.get("title") instanceof String text ? text.replaceAll("[\\p{Cntrl}\"`]", " ") : "";
        path = path.length() > 120 ? path.substring(0, 120) : path;
        title = title.length() > 80 ? title.substring(0, 80) : title;
        return path.isEmpty() ? "an unknown page" : "the page " + path + (title.isBlank() ? "" : " (" + title.strip() + ")");
    }

    /** The links that point at an existing page this user may open; others are dropped. */
    static List<Map<String, String>> links(JsonNode links, boolean admin) {
        List<Map<String, String>> valid = new ArrayList<>();
        if (links == null || !links.isArray()) {
            return valid;
        }
        for (JsonNode link : links) {
            String label = ToolLoop.text(link, "label");
            String path = ToolLoop.text(link, "path");
            if (label == null || label.isBlank() || path == null) {
                continue;
            }
            String bare = path.split("[?#]", 2)[0];
            boolean exists = PAGES.stream().anyMatch(page -> page.matcher(bare).matches())
                    || admin && ADMIN_PAGES.matcher(bare).matches();
            if (exists && valid.stream().noneMatch(seen -> seen.get("path").equals(bare)) && valid.size() < 4) {
                valid.add(Map.of("label", label.length() > 60 ? label.substring(0, 60) : label, "path", bare));
            }
        }
        return valid;
    }

    static Map<String, String> handoff(JsonNode handoff) {
        String assistant = ToolLoop.text(handoff, "assistant");
        String request = ToolLoop.text(handoff, "request");
        if (assistant == null || !HANDOFFS.containsKey(assistant) || request == null || request.isBlank()) {
            return null;
        }
        return Map.of("assistant", assistant, "path", HANDOFFS.get(assistant),
                "request", request.length() > 1000 ? request.substring(0, 1000) : request);
    }
}
