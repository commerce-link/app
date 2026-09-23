package pl.commercelink.web;

import org.junit.jupiter.api.Test;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import static org.assertj.core.api.Assertions.assertThat;

class DeliverySupplierCellRenderingTest {

    private static final String CELL =
            "<table><tr><td th:replace=\"~{fragments/delivery-supplier :: cell(${label}, ${shortcut})}\"></td></tr></table>";

    private final TemplateEngine engine = EnglishFragmentTemplateEngine.create();

    @Test
    void showsTheSyncedCounterpartyUnderTheSupplierLabel() {
        // when
        String html = engine.process(CELL, context("Amazon", "AmazonEuSarlPl"));

        // then
        assertThat(html).contains("<span>Amazon</span>").contains("Counterparty: AmazonEuSarlPl");
    }

    @Test
    void showsOnlyTheLabelBeforeAnyInvoiceSync() {
        // when
        String html = engine.process(CELL, context("Amazon", null));

        // then
        assertThat(html).contains("<span>Amazon</span>").doesNotContain("Counterparty");
    }

    @Test
    void doesNotRepeatAShortcutEqualToTheLabel() {
        // when
        String html = engine.process(CELL, context("Kosatec", "Kosatec"));

        // then
        assertThat(html).doesNotContain("Counterparty");
    }

    private Context context(String label, String shortcut) {
        Context context = new Context();
        context.setVariable("label", label);
        context.setVariable("shortcut", shortcut);
        return context;
    }
}
