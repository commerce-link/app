package pl.commercelink.web.offers;

import org.junit.jupiter.api.Test;
import pl.commercelink.web.offers.BasketViewPage.*;
import pl.commercelink.web.orders.AddressBlock;

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
        return new Row("Laptop Dell", "Laptopy", "DELL-1", 1, "6" + NBSP + "899,00", false);
    }

    private static Row mouse() {
        return new Row("Mysz", "Myszy", "MX-2", 2, "449,00", false);
    }

    private static Row service() {
        return new Row("Konfiguracja systemu", null, null, 1, "99,00", true);
    }

    private static Summary summary(DeliveryState state) {
        return new Summary("7" + NBSP + "797,00", null, null, state, "Kurier InPost", "15,00", "7" + NBSP + "812,00",
                "6" + NBSP + "351,22", "1" + NBSP + "460,78");
    }

    private static Summary discountedSummary() {
        return new Summary("7" + NBSP + "897,00", "LATO-10", "100,00", DeliveryState.CHOSEN, "Kurier InPost", "15,00",
                "7" + NBSP + "812,00", "6" + NBSP + "351,22", "1" + NBSP + "460,78");
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
                summary(DeliveryState.CHOSEN), new Customer(BILLING, BILLING, true));
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
                .contains("Przejdziesz do danych klienta. Po ich zatwierdzeniu powstanie zamówienie")
                .contains("href=\"/dashboard/basket/view/" + ID + "/order\"")
                .contains("Utwórz ofertę z koszyka")
                .contains("Pozycje i dane klienta przejdą do nowej oferty")
                .contains("href=\"/dashboard/offer/new?intent=copy&amp;sourceId=" + ID + "\"");
        assertThat(html.split("<h1", -1)).hasSize(2);
    }

    @Test
    void itemsShowQuantityTimesPriceWithoutSupplierCosts() {
        // given
        BasketViewPage page = fullPage();

        // when
        String html = html(page);

        // then: the client wants no market column or estimated margin on a basket priced by the shop
        assertThat(html).contains("1 × 6" + NBSP + "899,00")
                .contains("Usługi")
                .contains("Usługa")
                .contains("Ilość × cena (PLN)")
                .contains("Kopiuj kod producenta")
                .contains("<colgroup><col><col class=\"cl-col-price\"></colgroup>")
                .contains("colspan=\"2\"")
                .doesNotContain("Rynek")
                .doesNotContain("Szacowana marża")
                .doesNotContain("Brak w asortymencie")
                .doesNotContain("cl-margin");
    }

    @Test
    void summaryHasTotalsWithoutAnEstimate() {
        // given
        BasketViewPage page = fullPage();

        // when
        String html = html(page);

        // then
        assertThat(html).contains("Dostawa · Kurier InPost")
                .contains("Razem dla klienta")
                .doesNotContain("Szacowany koszt i zysk")
                .doesNotContain("basket-estimate")
                .doesNotContain("Zysk");
    }

    @Test
    void discountedSummaryNamesTheCodeAndTheAmount() {
        // given
        BasketViewPage page = page("Biuro", 3, null, List.of(laptop()), List.of(), discountedSummary(),
                new Customer(BILLING, BILLING, true));

        // when
        String html = html(page);

        // then
        assertThat(html).contains("Pozycje przed rabatem")
                .contains("Kod rabatowy LATO-10")
                .contains("−100,00 PLN");
    }

    @Test
    void summaryWithoutCodeHasNoDiscountRow() {
        // given
        BasketViewPage page = fullPage();

        // when
        String html = html(page);

        // then
        assertThat(html).contains("Pozycje")
                .doesNotContain("przed rabatem")
                .doesNotContain("Kod rabatowy");
    }

    @Test
    void unknownDeliveryIsExplained() {
        // given
        BasketViewPage page = page("Biuro", 3, null, List.of(laptop()), List.of(),
                summary(DeliveryState.UNKNOWN), new Customer(BILLING, BILLING, true));

        // when
        String html = html(page);

        // then
        assertThat(html).contains("nieznana opcja")
                .contains("Tej opcji dostawy nie ma już");
    }

    @Test
    void guestBasketExplainsTheMissingCustomerAndOffersItemsOnly() {
        // given
        BasketViewPage page = page(null, 3, null, List.of(laptop()), List.of(),
                summary(DeliveryState.NONE), new Customer(EMPTY_BLOCK, EMPTY_BLOCK, false));

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
                summary(DeliveryState.NONE),
                new Customer(EMPTY_BLOCK, EMPTY_BLOCK, false));

        // when
        String html = html(page);

        // then
        assertThat(html).contains("Koszyk jest pusty.")
                .doesNotContain("basket-estimate")
                .contains("aria-disabled=\"true\"")
                .contains("Pusty koszyk — nie ma z czego")
                .doesNotContain("data-cl-confirm-tone");
    }

    @Test
    void itemsDescriptionSitsOutsideTheCardHead() {
        // given
        BasketViewPage page = fullPage();

        // when
        String html = html(page);

        // then
        assertThat(html).containsPattern("</div>\\s*<p class=\"cl-card-desc\">Ceny brutto")
                .doesNotContain("cl-basket-note")
                .contains("cl-card-foot-note");
    }

    @Test
    void deletionPillOnlyWhenClose() {
        // given
        BasketViewPage closing = page("Biuro", 3, new Pill("offers.basket.pill.deletion.tomorrow", null),
                List.of(laptop()), List.of(), summary(DeliveryState.CHOSEN), new Customer(BILLING, BILLING, true));
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

    @Test
    void contentWrapperRendersWithoutTheParameterisedFragmentsInPlace() {
        // given
        BasketViewPage page = fullPage();

        // when
        String html = render("<div th:replace=\"~{offers/basket :: [layout:fragment='content']}\"></div>",
                Map.of("page", page));

        // then
        assertThat(html.split("<h1", -1)).hasSize(2);
        assertThat(html).contains("id=\"pozycje\"")
                .contains("id=\"summary-title\"")
                .contains("id=\"customer-title\"")
                .contains("id=\"facts-title\"")
                .contains("/js/menu.js")
                .contains("/js/copy-field.js")
                .contains("/js/confirm-dialog.js");
    }
}
