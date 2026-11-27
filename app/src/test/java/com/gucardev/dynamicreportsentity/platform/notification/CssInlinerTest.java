package com.gucardev.dynamicreportsentity.platform.notification;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CssInlinerTest {

    @Test
    void bodiesWithoutStyleBlocksStayAsTheyAre() {
        String html = "<p style=\"color: red\">{{x}} & y</p>";
        assertThat(CssInliner.inline(html)).isSameAs(html);
    }

    @Test
    void rulesApplyBySpecificityAndOrderAndInlineStylesWin() {
        String html = CssInliner.inline("""
                <style>
                  /* comment */
                  td { padding: 4px; color: black }
                  .data td { padding: 8px }
                  td { color: gray }
                  #total { font-weight: bold }
                  p { margin: 0 !important }
                </style>
                <table class="data"><tr><td>a</td><td id="total" style="color: red">b</td></tr></table>
                <p style="margin: 10px">c</p>""");

        assertThat(html).contains("<td style=\"padding: 8px; color: gray;\">a</td>",
                "<td id=\"total\" style=\"padding: 8px; color: red; font-weight: bold;\">b</td>",
                "<p style=\"margin: 0;\">c</p>").doesNotContain("<style");
    }

    @Test
    void whatCannotBeInlinedStaysInTheStyleBlock() {
        String html = CssInliner.inline("""
                <style>a { color: red } a:hover { color: blue } @media (max-width: 600px) { .card { padding: 8px } }
                </style><a href="#">x</a>""");

        assertThat(html).contains("<a href=\"#\" style=\"color: red;\">x</a>", "a:hover { color: blue }",
                "@media (max-width: 600px) { .card { padding: 8px } }");
    }
}
