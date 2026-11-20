package com.gucardev.dynamicreportsentity.platform.flow.mail;

import com.samskivert.mustache.BasicCollector;
import com.samskivert.mustache.Mustache;
import com.samskivert.mustache.MustacheException;
import com.samskivert.mustache.Template;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Renders templates written by business users with Mustache: no expressions, no code.
 * <ul>
 *   <li>{@link BasicCollector} only walks maps and lists, never Java objects through reflection, so a template
 *       cannot call methods ({@code {{x.class.classLoader}}} renders nothing).</li>
 *   <li>Partials ({@code {{> other}}}) are disabled: the default loader fails, no file is ever read.</li>
 *   <li>HTML bodies escape every value; subjects are plain text.</li>
 *   <li>Unknown names render as empty text instead of failing the run.</li>
 * </ul>
 */
@Component
public class TemplateRenderer {

    private static final Mustache.Collector MAPS_AND_LISTS_ONLY = new BasicCollector() {
        @Override
        public <K, V> Map<K, V> createFetcherCache() {
            return new ConcurrentHashMap<>();
        }
    };

    private static final Mustache.Compiler HTML = Mustache.compiler()
            .withCollector(MAPS_AND_LISTS_ONLY).escapeHTML(true).defaultValue("");
    private static final Mustache.Compiler TEXT = HTML.escapeHTML(false);

    /** A template that does not compile (e.g. an unclosed section); the message names the line. */
    public static class TemplateSyntaxException extends RuntimeException {
        public TemplateSyntaxException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public String renderHtml(String template, Map<String, ?> model) {
        return compile(HTML, template).execute(model);
    }

    public String renderText(String template, Map<String, ?> model) {
        return compile(TEXT, template).execute(model).replaceAll("[\\r\\n]+", " ").strip();
    }

    /** Throws {@link TemplateSyntaxException} if the template does not compile. */
    public void check(String template) {
        compile(HTML, template);
    }

    private static Template compile(Mustache.Compiler compiler, String template) {
        try {
            return compiler.compile(template);
        } catch (MustacheException e) {
            throw new TemplateSyntaxException(e.getMessage(), e);
        }
    }
}
