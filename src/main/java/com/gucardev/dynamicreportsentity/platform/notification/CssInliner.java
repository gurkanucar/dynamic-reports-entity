package com.gucardev.dynamicreportsentity.platform.notification;

import org.jsoup.Jsoup;
import org.jsoup.nodes.DataNode;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.jsoup.select.Selector;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Copies the rules of {@code <style>} blocks into the {@code style=""} attributes of the elements they match, because
 * Outlook and many webmail clients drop {@code <style>}. Template authors (and the assistant) can then write readable
 * CSS with classes.
 * <ul>
 *   <li>Rules apply in CSS order: lower specificity first, later rules win on ties; a declaration already written in
 *       {@code style=""} wins over all of them, {@code !important} ones over those without.</li>
 *   <li>What cannot be inlined stays in the {@code <style>} block for the clients that do read it: at-rules
 *       ({@code @media}, {@code @font-face}) and selectors with pseudo-classes ({@code a:hover}).</li>
 *   <li>A body without {@code <style>} is returned unchanged.</li>
 * </ul>
 */
public final class CssInliner {

    private static final Pattern COMMENT = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);
    private static final Pattern ID = Pattern.compile("#[\\w-]+");
    private static final Pattern CLASS_OR_ATTRIBUTE = Pattern.compile("\\.[\\w-]+|\\[[^]]*]");
    private static final Pattern ELEMENT = Pattern.compile("(?:^|[\\s>+~])([a-zA-Z][\\w-]*)");

    private record Rule(String selector, int specificity, int order, Map<String, String> declarations) {
    }

    private CssInliner() {
    }

    public static String inline(String html) {
        if (html == null || !html.toLowerCase().contains("<style")) {
            return html;
        }
        Document document = Jsoup.parse(html);
        document.outputSettings().prettyPrint(false);
        List<Rule> rules = new ArrayList<>();
        Map<Element, String> leftOver = new IdentityHashMap<>();
        for (Element style : document.select("style")) {
            StringBuilder keep = new StringBuilder();
            parse(COMMENT.matcher(style.data()).replaceAll(""), rules, keep);
            leftOver.put(style, keep.toString().strip());
        }
        rules.sort(Comparator.comparingInt(Rule::specificity).thenComparingInt(Rule::order));

        Map<Element, Map<String, String>> computed = new IdentityHashMap<>();
        Map<Element, Map<String, String>> important = new IdentityHashMap<>();
        for (Rule rule : rules) {
            Elements matches;
            try {
                matches = document.select(rule.selector());
            } catch (Selector.SelectorParseException e) {
                continue;   // a selector jsoup does not understand: the rule simply is not inlined
            }
            for (Element element : matches) {
                rule.declarations().forEach((property, value) -> {
                    if (value.endsWith("!important")) {
                        important.computeIfAbsent(element, key -> new LinkedHashMap<>())
                                .put(property, value.substring(0, value.length() - "!important".length()).strip());
                    } else {
                        computed.computeIfAbsent(element, key -> new LinkedHashMap<>()).put(property, value);
                    }
                });
            }
        }
        for (Element element : union(computed, important)) {
            Map<String, String> result = new LinkedHashMap<>(computed.getOrDefault(element, Map.of()));
            result.putAll(declarations(element.attr("style")));
            result.putAll(important.getOrDefault(element, Map.of()));
            StringBuilder style = new StringBuilder();
            result.forEach((property, value) -> style.append(property).append(": ").append(value).append("; "));
            element.attr("style", style.toString().strip());
        }
        leftOver.forEach((style, keep) -> {
            if (keep.isEmpty()) {
                style.remove();
            } else {
                style.empty().appendChild(new DataNode(keep));
            }
        });
        return document.outerHtml();
    }

    /** Splits a style sheet into inlinable rules and text to keep (at-rules, pseudo-class selectors). */
    private static void parse(String css, List<Rule> rules, StringBuilder keep) {
        int position = 0;
        while (position < css.length()) {
            int open = css.indexOf('{', position);
            if (open < 0) {
                break;
            }
            String prelude = css.substring(position, open).strip();
            int close = matchingBrace(css, open);
            String body = css.substring(open + 1, close);
            position = close + 1;
            if (prelude.startsWith("@")) {
                keep.append(prelude).append(" {").append(body).append("}\n");
                continue;
            }
            Map<String, String> declarations = declarations(body);
            List<String> kept = new ArrayList<>();
            for (String selector : prelude.split(",")) {
                String trimmed = selector.strip();
                if (trimmed.isEmpty()) {
                    continue;
                }
                if (trimmed.contains(":")) {
                    kept.add(trimmed);
                } else {
                    rules.add(new Rule(trimmed, specificity(trimmed), rules.size(), declarations));
                }
            }
            if (!kept.isEmpty()) {
                keep.append(String.join(", ", kept)).append(" {").append(body).append("}\n");
            }
        }
    }

    private static int matchingBrace(String css, int open) {
        int depth = 0;
        for (int i = open; i < css.length(); i++) {
            char c = css.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}' && --depth == 0) {
                return i;
            }
        }
        return css.length() - 1;
    }

    private static Map<String, String> declarations(String text) {
        Map<String, String> declarations = new LinkedHashMap<>();
        if (text == null) {
            return declarations;
        }
        for (String declaration : text.split(";")) {
            int colon = declaration.indexOf(':');
            if (colon > 0) {
                String property = declaration.substring(0, colon).strip().toLowerCase();
                String value = declaration.substring(colon + 1).strip();
                if (!property.isEmpty() && !value.isEmpty()) {
                    declarations.put(property, value);
                }
            }
        }
        return declarations;
    }

    /** (ids, classes and attributes, elements) as one comparable number. */
    private static int specificity(String selector) {
        return count(ID.matcher(selector)) * 10_000 + count(CLASS_OR_ATTRIBUTE.matcher(selector)) * 100
                + count(ELEMENT.matcher(selector.replaceAll("[#.][\\w-]+|\\[[^]]*]", " ")));
    }

    private static int count(Matcher matcher) {
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    private static List<Element> union(Map<Element, ?> first, Map<Element, ?> second) {
        Map<Element, Boolean> all = new IdentityHashMap<>();
        first.keySet().forEach(element -> all.put(element, true));
        second.keySet().forEach(element -> all.put(element, true));
        return new ArrayList<>(all.keySet());
    }
}
