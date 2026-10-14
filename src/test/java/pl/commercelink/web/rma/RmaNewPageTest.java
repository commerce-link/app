package pl.commercelink.web.rma;

import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RmaNewPageTest {

    @Test
    void withPageErrorReplacesOnlyThePageError() throws Exception {
        // given every component set, so a component dropped or swapped by the copy shows up
        RmaNewPage page = new RmaNewPage(RmaNewPage.State.ORDER, "query", "/back", "Back", "order", "lead",
                new RmaNewPage.Header("o-1", "o1", "/o", "status", "is-ok", "01.10.2026", "Anna", "Sklep"),
                new RmaNewPage.Choice("title", List.of(), "limit"), "not found",
                new RmaNewPage.Ineligible("title", "desc", "/o", "open"),
                List.of(RmaNewTemplateTest.row(0, "i1", true, null)), "empty",
                new RmaNewPage.Summary("a@x.pl", 1, 1, "100,00", true), "old error",
                Map.of("email", "summary"), Map.of("email", "field"));

        // when
        RmaNewPage copy = page.withPageError("new error");

        // then
        assertThat(copy.pageError()).isEqualTo("new error");
        for (RecordComponent component : RmaNewPage.class.getRecordComponents()) {
            if (!component.getName().equals("pageError")) {
                Object original = component.getAccessor().invoke(page);
                assertThat(component.getAccessor().invoke(copy)).as(component.getName()).isNotNull().isSameAs(original);
            }
        }
    }
}
