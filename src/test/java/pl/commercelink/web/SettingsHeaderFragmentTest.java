package pl.commercelink.web;

import org.junit.jupiter.api.Test;
import org.thymeleaf.context.Context;

import static org.assertj.core.api.Assertions.assertThat;

class SettingsHeaderFragmentTest {

    @Test
    void subpageWithActionsRendersBackLinkTitleLeadAndActions() {
        // given
        String template = "<div th:replace=\"~{fragments/settings-header :: subpageWithActions('/dashboard/catalogs', 'Product catalog', "
                + "'Graphics cards', 'Manual · PIM: Graphics cards', ~{::a})}\"></div>"
                + "<a class=\"cl-button is-primary\" href=\"/x\">Add products</a>";

        // when
        String html = EnglishFragmentTemplateEngine.create().process(template, new Context());

        // then
        assertThat(html).contains("class=\"cl-back\"").contains("href=\"/dashboard/catalogs\"").contains("Product catalog")
                .contains("<h1 class=\"cl-page-title\">Graphics cards</h1>")
                .contains("<p class=\"cl-page-lead\">Manual · PIM: Graphics cards</p>")
                .contains("class=\"cl-page-actions\"").contains("Add products");
        assertThat(html.indexOf("cl-page-title")).isLessThan(html.indexOf("cl-page-actions"));
    }

    @Test
    void subpageWithActionsOmitsLeadAndActionsWhenNull() {
        // given
        String template = "<div th:replace=\"~{fragments/settings-header :: subpageWithActions('/back', 'Back', 'Title', null, null)}\"></div>";

        // when
        String html = EnglishFragmentTemplateEngine.create().process(template, new Context());

        // then
        assertThat(html).doesNotContain("cl-page-lead").doesNotContain("cl-page-actions").contains("<h1 class=\"cl-page-title\">Title</h1>");
    }

    @Test
    void subpageWithActionsRendersFragmentLead() {
        // given
        String template = "<div th:replace=\"~{fragments/settings-header :: subpageWithActions('/dashboard/catalogs', 'Product catalog', "
                + "'Graphics cards', ~{::leadBlock}, null)}\"></div>"
                + "<span th:fragment=\"leadBlock\"><span class=\"cl-status is-neutral\">Manual</span> · PIM: Graphics cards</span>";

        // when
        String html = EnglishFragmentTemplateEngine.create().process(template, new Context());

        // then
        int leadOpen = html.indexOf("<p class=\"cl-page-lead\">");
        int leadClose = html.indexOf("</p>", leadOpen);
        int pillIndex = html.indexOf("<span class=\"cl-status is-neutral\">Manual</span>");
        assertThat(leadOpen).isNotEqualTo(-1);
        assertThat(leadClose).isNotEqualTo(-1);
        assertThat(pillIndex).isGreaterThan(leadOpen).isLessThan(leadClose);
        assertThat(html.substring(leadOpen, leadClose)).contains("PIM: Graphics cards");
    }
}
