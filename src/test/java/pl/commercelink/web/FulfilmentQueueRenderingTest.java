package pl.commercelink.web;

import org.junit.jupiter.api.Test;
import pl.commercelink.web.fulfilment.FulfilmentQueuePage;
import pl.commercelink.web.fulfilment.FulfilmentQueuePage.EmptyState;
import pl.commercelink.web.fulfilment.FulfilmentQueuePage.GroupKind;
import pl.commercelink.web.fulfilment.FulfilmentQueueRow;
import pl.commercelink.web.settings.SettingsTemplateRenderer;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Renders the fulfilment queue template with a real model and the Polish bundle. */
class FulfilmentQueueRenderingTest {

    private static FulfilmentQueueRow row(String id, int items) {
        return new FulfilmentQueueRow(id, "/dashboard/orders/" + id, "#" + id, "Allegro", "nr 5749922740", "Anna Nowak",
                "Kraków", "anna@example.com", "02.10 09:14", "05.10", "po terminie: 3 dni", "is-bad", items);
    }

    private static FulfilmentQueuePage group(boolean superAdmin) {
        return new FulfilmentQueuePage(GroupKind.WAREHOUSE, superAdmin ? "Sklep Demo" : null, List.of(row("o1", 4), row("o2", 2)), 2,
                List.of("s1", "s2", "o1", "o2"),
                superAdmin ? "/dashboard/store/store-1/orders/fulfilment" : "/dashboard/orders/fulfilment", 6, null, superAdmin);
    }

    private static String render(FulfilmentQueuePage page) {
        return SettingsTemplateRenderer.render("fulfilment-queue", Map.of("page", page));
    }

    @Test
    void aGroupRendersTickedRowsTheStrategyButtonsAndTheNarrowingFields() {
        // when
        String html = render(group(false));

        // then
        assertThat(html).contains("<h1").contains("Kolejka realizacji").contains("Zamówienia magazynowe").contains("Magazyn sklepu")
                .contains("action=\"/dashboard/orders/fulfilment\"")
                .contains("name=\"selectedOrders\"").contains("value=\"o1\"").contains("data-items=\"4\"")
                .contains("name=\"pathSelector\" value=\"default\"").contains("name=\"pathSelector\" value=\"suggest\"")
                .contains("name=\"pathSelector\" value=\"suggest-exact\"")
                .contains("name=\"onlyWithProfit\"").contains("name=\"onlyLocalSuppliers\"")
                .contains("name=\"onlyMultiOrder\"").contains("name=\"orderByOrder\"")
                .contains("<span>Pomiń</span>").contains("name=\"orderIds\" value=\"s1\"")
                .contains("po terminie: 3 dni").contains("2 z 2").contains("fulfilment-queue.js")
                .doesNotContain("??");
        String content = html.substring(html.indexOf("cl-page-body is-wide"));
        assertThat(content).doesNotContain("style=\"").doesNotContain("is-bordered").doesNotContain("Sklep:");
        assertThat(html.split("name=\"selectedOrders\"", -1)).hasSize(3);
        assertThat(html.split("checked", -1).length - 1).isGreaterThanOrEqualTo(2);
        assertThat(html).contains("value=\"default\" class=\"cl-button is-primary\"")
                .contains("value=\"suggest\" class=\"cl-button\"").contains("value=\"suggest-exact\" class=\"cl-button\"");
        assertThat(html.indexOf("value=\"default\"")).isLessThan(html.indexOf("value=\"suggest\""));
    }

    @Test
    void skippedOrdersAreRestoredFromAButtonNextToSkip() {
        // when
        String html = render(group(false));

        // then
        String actions = html.substring(html.indexOf("cl-page-actions"), html.indexOf("cl-page-lead"));
        assertThat(actions).contains("href=\"/dashboard/fulfilment/queue\"").contains("Przywróć pominięte: 2")
                .contains("fa-undo-alt").contains("<span>Pomiń</span>");
        assertThat(actions.indexOf("Przywróć pominięte")).isLessThan(actions.indexOf("<span>Pomiń</span>"));
        assertThat(html).doesNotContain("Pominięte zamówienia: 2");
    }

    @Test
    void withNothingSkippedThereIsNoRestoreButton() {
        // given
        FulfilmentQueuePage page = new FulfilmentQueuePage(GroupKind.DROPSHIP, null, List.of(row("o1", 1)), 0, List.of("o1"),
                "/dashboard/orders/fulfilment", 1, null, false);

        // when
        String html = render(page);

        // then
        String actions = html.substring(html.indexOf("cl-page-actions"), html.indexOf("cl-page-lead"));
        assertThat(actions).contains("<span>Pomiń</span>").doesNotContain("Przywróć").doesNotContain("fa-undo-alt");
        assertThat(html).doesNotContain("??");
    }

    @Test
    void theGuideExplainsSkippingAndRestoring() {
        // when
        String html = render(group(false));

        // then
        assertThat(html).contains("Pomiń i przywróć").contains("nic nie zmienia w zamówieniach");
    }

    @Test
    void superAdminSeesTheStoreAndPostsToTheStoreScopedPath() {
        // when
        String html = render(group(true));

        // then
        assertThat(html).contains("Sklep:").contains("Sklep Demo").contains("action=\"/dashboard/store/store-1/orders/fulfilment\"")
                .doesNotContain("??");
    }

    @Test
    void anEmptyQueueShowsNoGroupNoSkipAndNoSidePanel() {
        // given
        FulfilmentQueuePage page = new FulfilmentQueuePage(null, null, List.of(), 0, List.of(), null, 0, EmptyState.NONE_WAITING, false);

        // when
        String html = render(page);

        // then
        assertThat(html).contains("Nic nie czeka na zamówienie u dostawców").contains("Przejdź do zamówień")
                .doesNotContain("fa-forward").doesNotContain("pathSelector").doesNotContain("Pominięte zamówienia")
                .doesNotContain("??");
    }

    @Test
    void everythingSkippedOffersToRestore() {
        // given
        FulfilmentQueuePage page = new FulfilmentQueuePage(null, null, List.of(), 7, List.of("a"), null, 0, EmptyState.ALL_SKIPPED, false);

        // when
        String html = render(page);

        // then
        assertThat(html).contains("Pominięte zostały wszystkie czekające zamówienia").contains("Pominięte zamówienia: 7.")
                .contains("Przywróć pominięte").contains("href=\"/dashboard/fulfilment/queue\"")
                .doesNotContain("fa-forward").doesNotContain("??");
    }

    @Test
    void theOrderedDateHasALabelledSubLineUnderTheNumberAndASecondaryColumn() {
        // when
        String html = render(group(false));

        // then
        assertThat(html).contains("<span class=\"cl-table-sub cl-queue-ordered-sub\"><span class=\"cl-queue-label\">Złożone:</span> "
                + "<span class=\"cl-queue-ordered-date\">02.10 09:14</span></span>");
        String key = html.substring(html.indexOf("<th scope=\"row\" class=\"cl-table-key\">"));
        assertThat(key.substring(0, key.indexOf("</th>"))).contains("cl-queue-ordered-sub");
        assertThat(html).contains("<th scope=\"col\" class=\"is-secondary-column\">Złożone</th>")
                .contains("<td class=\"is-secondary-column\">")
                .contains("class=\"cl-card cl-queue-card\"");
    }
}
