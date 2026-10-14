package pl.commercelink.web.offers;

import org.junit.jupiter.api.Test;
import pl.commercelink.web.offers.BasketViewPage.*;
import pl.commercelink.web.orders.AddressBlock;
import pl.commercelink.web.orders.ItemMargin;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.commercelink.web.settings.SettingsTemplateRenderer.render;

class BasketViewRenderingTest {

    private static final String FRAGMENT = "<section th:replace=\"~{offers/basket :: page}\"></section>";
    private static final String ID = "8f3a21c7-1111-2222-3333-444455556666";
    private static final String NBSP = " ";

    private static final AddressBlock BILLING = new AddressBlock("Marta Zielińska", "Biuro", "6762468313",
            "ul. Wielicka 28/4", "30-552 Kraków", "Polska", "biuro@x.pl", "+48 601");
    private static final AddressBlock EMPTY_BLOCK = new AddressBlock(null, null, null, null, null, null, null, null);

    private static Row laptop() {
        return new Row("Laptop Dell", "Laptopy", "DELL-1", 1, "6" + NBSP + "899,00", false,
                ItemMargin.of(6899, 5949, 1.23), new Market(false, "5" + NBSP + "810,00", "5" + NBSP + "949,00", 5, 41));
    }

    private static Row mouse() {
        return new Row("Mysz", "Myszy", "MX-2", 2, "449,00", false, ItemMargin.of(449, 0, 1.23), Market.none());
    }

    private static Row service() {
        return new Row("Konfiguracja systemu", null, null, 1, "99,00", true, null, null);
    }

    private static Summary summary(DeliveryState state, Estimate estimate) {
        return new Summary("7" + NBSP + "797,00", state, "Kurier InPost", "15,00", "7" + NBSP + "812,00",
                "6" + NBSP + "351,22", "1" + NBSP + "460,78", estimate);
    }

    private static Estimate okEstimate() {
        return new Estimate(EstimateState.OK, "5" + NBSP + "949,00", "850,00", "691,06", "12,3", 1);
    }

    private static BasketViewPage page(String client, int itemsCount, Pill pill, List<Row> products, List<Row> services,
                                       Summary summary, Customer customer) {
        return new BasketViewPage(ID, "8f3a21c7", "/dashboard/offers?segment=baskets",
                "/dashboard/basket/view/" + ID + "/order", "/dashboard/offer/new?intent=copy&sourceId=" + ID,
                client, "12.10.2026 14:05", "7" + NBSP + "812,00", itemsCount, pill, products, services, summary, customer,
                new Facts("12.10.2026 14:05", "order.source.type.WebStore", "partner1", null, null, 30));
    }

    private static BasketViewPage fullPage() {
        return page("Biuro", 3, null, List.of(laptop(), mouse()), List.of(service()),
                summary(DeliveryState.CHOSEN, okEstimate()), new Customer(BILLING, BILLING, true));
    }

    private static String html(BasketViewPage page) {
        return render(FRAGMENT, Map.of("page", page));
    }

    @Test
    void headerIsARecordWithBackLinkCopyAndConfirmedPrimaryAction() {
        // given
        BasketViewPage page = fullPage();

        // when
        String html = html(page);

        // then
        assertThat(html).contains("class=\"cl-back\" href=\"/dashboard/offers?segment=baskets\"")
                .contains("Koszyki ze sklepu")
                .contains("<h1 class=\"cl-page-title\">Koszyk 8f3a21c7</h1>")
                .contains("data-cl-copy=\"" + ID + "\"")
                .contains("data-cl-confirm-tone=\"primary\"")
                .contains("Utworzyć zamówienie z koszyka 8f3a21c7?")
                .contains("href=\"/dashboard/basket/view/" + ID + "/order\"")
                .contains("Utwórz ofertę z koszyka")
                .contains("Pozycje i dane klienta przejdą do nowej oferty")
                .contains("href=\"/dashboard/offer/new?intent=copy&amp;sourceId=" + ID + "\"");
        assertThat(html.split("<h1", -1)).hasSize(2);
    }

    @Test
    void itemsShowQuantityTimesPriceWithEstimatedMarginAndTheMarket() {
        // given
        BasketViewPage page = fullPage();

        // when
        String html = html(page);

        // then
        assertThat(html).contains("1 × 6" + NBSP + "899,00")
                .contains("Szacowana marża")
                .contains("5" + NBSP + "810,00 → 5" + NBSP + "949,00")
                .contains("dostawcy: 5 · szt.: 41")
                .contains("Brak w asortymencie")
                .contains("Usługi")
                .contains("Usługa")
                .contains("Ilość × cena (PLN)")
                .contains("Rynek (PLN)")
                .contains("Kopiuj kod producenta");
    }

    @Test
    void summaryHasTotalsAndAnOpenEstimate() {
        // given
        BasketViewPage page = fullPage();

        // when
        String html = html(page);

        // then
        assertThat(html).contains("Dostawa · Kurier InPost")
                .contains("Razem dla klienta")
                .contains("Szacowany koszt i zysk")
                .contains("<details class=\"cl-disclosure\" id=\"basket-estimate\" open")
                .contains("Pozycje bez ofert: 1");
    }

    @Test
    void unknownDeliveryAndUnavailableEstimateAreExplained() {
        // given
        BasketViewPage page = page("Biuro", 3, null, List.of(laptop()), List.of(),
                summary(DeliveryState.UNKNOWN, new Estimate(EstimateState.UNAVAILABLE, null, null, null, null, 0)),
                new Customer(BILLING, BILLING, true));

        // when
        String html = html(page);

        // then
        assertThat(html).contains("nieznana opcja")
                .contains("Tej opcji dostawy nie ma już")
                .contains("Nie udało się pobrać ofert dostawców");
    }

    @Test
    void guestBasketExplainsTheMissingCustomerAndOffersItemsOnly() {
        // given
        BasketViewPage page = page(null, 3, null, List.of(laptop()), List.of(),
                summary(DeliveryState.NONE, okEstimate()), new Customer(EMPTY_BLOCK, EMPTY_BLOCK, false));

        // when
        String html = html(page);

        // then
        assertThat(html).contains("gość, bez danych")
                .contains("Klient nie podał danych")
                .contains("Pozycje przejdą do nowej oferty")
                .doesNotContain("cl-address");
    }

    @Test
    void sameAddressIsSaidOnce() {
        // given
        BasketViewPage page = fullPage();

        // when
        String html = html(page);

        // then
        assertThat(html).contains("taki sam jak dane do faktury");
        assertThat(html.split("ul\\. Wielicka 28/4", -1)).hasSize(2);
    }

    @Test
    void emptyBasketDisablesCreateOrder() {
        // given
        BasketViewPage page = page(null, 0, null, List.of(), List.of(),
                summary(DeliveryState.NONE, new Estimate(EstimateState.NO_PRODUCTS, null, null, null, null, 0)),
                new Customer(EMPTY_BLOCK, EMPTY_BLOCK, false));

        // when
        String html = html(page);

        // then
        assertThat(html).contains("Koszyk jest pusty.")
                .contains("aria-disabled=\"true\"")
                .contains("Pusty koszyk — nie ma z czego")
                .doesNotContain("data-cl-confirm-tone");
    }

    @Test
    void deletionPillOnlyWhenClose() {
        // given
        BasketViewPage closing = page("Biuro", 3, new Pill("offers.basket.pill.deletion.tomorrow", null),
                List.of(laptop()), List.of(), summary(DeliveryState.CHOSEN, okEstimate()), new Customer(BILLING, BILLING, true));
        BasketViewPage far = fullPage();

        // when
        String withPill = html(closing);
        String withoutPill = html(far);

        // then
        assertThat(withPill).contains("cl-status is-warn").contains("Usunięcie jutro");
        assertThat(withoutPill).doesNotContain("Usunięcie jutro");
    }

    @Test
    void pageUsesOnlyTheDesignSystem() {
        // given
        BasketViewPage page = fullPage();

        // when
        String html = html(page);

        // then
        assertThat(html).doesNotContain("??")
                .doesNotContain("class=\"button")
                .doesNotContain("class=\"table")
                .doesNotContain("class=\"box")
                .doesNotContain("style=\"")
                .doesNotContain(" button is-primary");
    }
}
