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
                List.of("s1", "s2", "o1", "o2"), "1,1,2", "/dashboard/fulfilment/queue?orderIds=s1&skippedGroups=1", true,
                superAdmin ? "/dashboard/store/store-1/orders/fulfilment" : "/dashboard/orders/fulfilment", 6, null, superAdmin,
                List.of("s1", "s2"), "1,1");
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
                .contains("name=\"pathSelector\" value=\"default\"").doesNotContain("name=\"pathSelector\" value=\"suggest\"")
                .contains("name=\"pathSelector\" value=\"suggest-exact\"").contains("Zasugeruj dostawców")
                .doesNotContain("Zasugeruj dokładnie")
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
                .contains("value=\"suggest-exact\" class=\"cl-button\"");
        assertThat(html.split("name=\"pathSelector\"", -1)).hasSize(3);
        assertThat(html.indexOf("value=\"default\"")).isLessThan(html.indexOf("value=\"suggest-exact\""));
    }

    @Test
    void afterTwoSkippedGroupsTheHeaderOffersBackStartOverAndSkip() {
        // when
        String html = render(group(false));

        // then
        String actions = html.substring(html.indexOf("cl-page-actions"), html.indexOf("cl-page-lead"));
        assertThat(actions).contains("href=\"/dashboard/fulfilment/queue?orderIds=s1&amp;skippedGroups=1\"")
                .contains("<span>Wróć</span>").contains("<span>Zacznij od początku</span>").contains("href=\"/dashboard/fulfilment/queue\"")
                .contains("name=\"skippedGroups\" value=\"1,1,2\"").contains("<span>Pomiń</span>");
        assertThat(actions.indexOf("Wróć")).isLessThan(actions.indexOf("Zacznij od początku"));
        assertThat(actions.indexOf("Zacznij od początku")).isLessThan(actions.indexOf("<span>Pomiń</span>"));
        assertThat(html).doesNotContain("Pominięte zamówienia: 2");
    }

    @Test
    void afterOneSkippedGroupThereIsBackButNoStartOver() {
        // given
        FulfilmentQueuePage page = new FulfilmentQueuePage(GroupKind.DROPSHIP, null, List.of(row("o1", 1)), 3,
                List.of("w1", "w2", "w3", "o1"), "3,1", "/dashboard/fulfilment/queue", false, "/dashboard/orders/fulfilment", 1, null, false, List.of(), "");

        // when
        String html = render(page);

        // then
        String actions = html.substring(html.indexOf("cl-page-actions"), html.indexOf("cl-page-lead"));
        assertThat(actions).contains("<span>Wróć</span>").doesNotContain("Zacznij od początku").contains("<span>Pomiń</span>");
    }

    @Test
    void withNothingSkippedOnlySkipIsOffered() {
        // given
        FulfilmentQueuePage page = new FulfilmentQueuePage(GroupKind.DROPSHIP, null, List.of(row("o1", 1)), 0, List.of("o1"),
                "1", null, false, "/dashboard/orders/fulfilment", 1, null, false, List.of(), "");

        // when
        String html = render(page);

        // then
        String actions = html.substring(html.indexOf("cl-page-actions"), html.indexOf("cl-page-lead"));
        assertThat(actions).contains("<span>Pomiń</span>").doesNotContain("Wróć").doesNotContain("Zacznij od początku");
        assertThat(html).doesNotContain("??");
    }

    @Test
    void theGuideExplainsSkippingAndRestoring() {
        // when
        String html = render(group(false));

        // then
        assertThat(html).contains("Pomiń i wróć").contains("nic nie zmienia w zamówieniach");
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
        FulfilmentQueuePage page = new FulfilmentQueuePage(null, null, List.of(), 0, List.of(), "", null, false, null, 0, EmptyState.NONE_WAITING, false, List.of(), "");

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
        FulfilmentQueuePage page = new FulfilmentQueuePage(null, null, List.of(), 7, List.of("a"), "6,1", "/dashboard/fulfilment/queue?orderIds=a&skippedGroups=6", true, null, 0, EmptyState.ALL_SKIPPED, false, List.of(), "");

        // when
        String html = render(page);

        // then
        assertThat(html).contains("Pominięte zostały wszystkie czekające zamówienia").contains("Pominięte zamówienia: 7.")
                .contains("<span>Wróć</span>").contains("href=\"/dashboard/fulfilment/queue?orderIds=a&amp;skippedGroups=6\"")
                .contains("<span>Zacznij od początku</span>").contains("href=\"/dashboard/fulfilment/queue\"")
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

    @Test
    void theFormSendsTheSkipStateSoTheSelectionPageCanReturnToTheSameQueue() {
        // when
        String html = render(group(false));

        // then
        String form = html.substring(html.indexOf("data-cl-queue"));
        assertThat(form).contains("name=\"skippedOrderIds\" value=\"s1\"").contains("name=\"skippedOrderIds\" value=\"s2\"")
                .contains("name=\"skippedGroups\" value=\"1,1\"");
    }

    @Test
    void theOutcomeOfTheLastSelectionIsShownAboveTheGroup() {
        // given
        Map<String, Object> model = Map.of("page", group(false),
                "orderNotice", new pl.commercelink.web.orders.OrderNotice("is-ok", "Zapisano dobór", "/dashboard/deliveries/preview", "Oczekujące dostawy ›"));

        // when
        String html = SettingsTemplateRenderer.render("fulfilment-queue", model);

        // then
        assertThat(html).contains("cl-alert is-ok").contains("Zapisano dobór")
                .contains("href=\"/dashboard/deliveries/preview\"");
        assertThat(html.indexOf("Zapisano dobór")).isLessThan(html.indexOf("queue-group-title"));
    }

    @Test
    void theGuideNoLongerPromisesDeliveriesOnTheNextScreen() {
        // when
        String html = render(group(false));

        // then
        assertThat(html).doesNotContain("dostawy utworzą się").contains("Oczekujących dostaw");
    }
}
