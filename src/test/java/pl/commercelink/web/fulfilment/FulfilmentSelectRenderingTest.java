package pl.commercelink.web.fulfilment;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import pl.commercelink.inventory.supplier.SupplierLabelMap;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.orders.ShippingDetails;
import pl.commercelink.orders.fulfilment.FulfilmentForm;
import pl.commercelink.orders.fulfilment.FulfilmentGroup;
import pl.commercelink.orders.fulfilment.FulfilmentType;
import pl.commercelink.orders.fulfilment.FulfilmentVariant;
import pl.commercelink.orders.fulfilment.UnmatchedItem;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.settings.SettingsTemplateRenderer;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static pl.commercelink.web.fulfilment.FulfilmentSelectPageFactoryTest.form;
import static pl.commercelink.web.fulfilment.FulfilmentSelectPageFactoryTest.offer;

/** Renders fulfilment.html with a page built by the real factory and the Polish bundle. */
class FulfilmentSelectRenderingTest {

    private static final Locale PL = Locale.forLanguageTag("pl");
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 8);

    private FulfilmentSelectPageFactory factory;
    private StoresRepository storesRepository;
    private OrdersRepository ordersRepository;
    private SupplierLabelMap labels;

    @BeforeEach
    void setUp() {
        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding("UTF-8");
        storesRepository = mock(StoresRepository.class);
        ordersRepository = mock(OrdersRepository.class);
        factory = new FulfilmentSelectPageFactory(messages, storesRepository, ordersRepository);
        labels = FulfilmentSelectPageFactoryTest.labels();
    }

    private String render(FulfilmentForm form, FulfilmentSelectPage page) {
        return SettingsTemplateRenderer.render("fulfilment", Map.of("form", form, "page", page, "supplierLabels", labels));
    }

    private static String content(String html) {
        return html.substring(html.indexOf("cl-page-body is-wide"));
    }

    /** The page is a warehouse page only when the orders are known to be warehouse-fulfilled. */
    private void warehouseOrder(String id) {
        Order order = new Order("store-1");
        order.setOrderId(id);
        order.setFulfilmentType(FulfilmentType.WarehouseFulfilment);
        order.setShippingDetails(new ShippingDetails());
        when(ordersRepository.findById("store-1", id)).thenReturn(order);
    }

    private FulfilmentForm warehouseGroup() {
        warehouseOrder("w1-a");
        warehouseOrder("w2-b");
        FulfilmentGroup cheap = offer("Elko-k1", "CPU", 100, true, "w1-a:i1:200");
        FulfilmentGroup dear = offer("AB-k2", "CPU", 120, false, "w1-a:i1:200");
        FulfilmentGroup gpu = offer("Elko-k1", "GPU", 300, true, "w1-a:i2:500", "w2-b:i3:500");
        FulfilmentForm form = form(List.of("w1-a", "w2-b"), cheap, dear, gpu);
        form.setPathSelector("suggest-exact");
        form.setOnlyWithProfit(true);
        form.setSkippedOrderIds(List.of("s1"));
        form.setSkippedGroups("1");
        form.setOrderCountAtStart(2);
        form.setVariants(List.of(new FulfilmentVariant(List.of(cheap.getId(), gpu.getId()), List.of("Elko-k1"), 1, 520.0, true, true)));
        form.setUnmatched(List.of(new UnmatchedItem("w2-b", "i4", "Logitech MX Keys", 1, 449, UnmatchedItem.Reason.NARROWED)));
        return form;
    }

    @Test
    void aWarehouseGroupRendersHeaderBarTableAndSummary() {
        // given
        FulfilmentForm form = warehouseGroup();

        // when
        String html = render(form, factory.forOrders(form, "store-1", false, labels, TODAY, PL));

        // then
        assertThat(html).contains("<h1").contains("Dobór dostawców").contains("Kolejka realizacji")
                .contains("href=\"/dashboard/fulfilment/queue?orderIds=s1&amp;skippedGroups=1\"")
                .contains("Magazyn sklepu").contains("Sugestia dokładna").contains("Tylko źródła z zyskiem")
                .contains("data-cl-select").contains("action=\"/dashboard/orders/fulfilment/commit\"")
                .contains("formaction=\"/dashboard/orders/fulfilment/commitAndContinue\"")
                .contains("Zatwierdź dobór").contains("Zatwierdź i dobierz resztę").doesNotContain("Pomiń to zamówienie")
                .contains("data-cl-variant").contains("Wybiorę sam").contains("najtańsza")
                .contains("data-cl-coverage").contains("data-order=\"w1-a\"")
                .contains("data-provider=\"Elko-k1\"").contains("data-provider-label=\"Elko\"")
                .contains("Bez oferty").contains("Logitech MX Keys").contains("brak oferty po zawężeniach")
                .contains("fulfilment-select.js").contains("submit-once.js")
                .doesNotContain("??");
        assertThat(content(html)).doesNotContain("style=\"").doesNotContain("redirectUrl")
                .doesNotContain("isSuperAdmin").doesNotContain("innerHTML").doesNotContain("is-bordered");
    }

    @Test
    void theFormCarriesEveryFieldThePostNeedsButNoRedirect() {
        // given
        FulfilmentForm form = warehouseGroup();

        // when
        String html = render(form, factory.forOrders(form, "store-1", false, labels, TODAY, PL));

        // then
        assertThat(html).contains("name=\"fulfilmentType\" value=\"orders\"").contains("name=\"pathSelector\" value=\"suggest-exact\"")
                .contains("name=\"onlyWithProfit\" value=\"true\"").contains("name=\"orderByOrder\" value=\"false\"")
                .contains("name=\"selectedOrders[0]\" value=\"w1-a\"").contains("name=\"selectedOrders[1]\" value=\"w2-b\"")
                .contains("name=\"skippedOrderIds\" value=\"s1\"").contains("name=\"skippedGroups\" value=\"1\"")
                .contains("name=\"orderCountAtStart\" value=\"2\"").contains("data-cl-payload");
    }

    @Test
    void acceptedOffersStartTickedAndHighlighted() {
        // given
        FulfilmentForm form = warehouseGroup();

        // when
        String html = render(form, factory.forOrders(form, "store-1", false, labels, TODAY, PL));

        // then
        String dear = html.substring(html.indexOf("data-provider=\"AB-k2\""));
        dear = dear.substring(0, dear.indexOf("</tr>"));
        assertThat(html.split("data-cl-offer-check", -1)).hasSize(4);
        assertThat(html).contains("class=\"is-on\"");
        assertThat(dear).doesNotContain("checked");
        assertThat(html.substring(0, html.indexOf("data-provider=\"AB-k2\""))).contains("class=\"is-off\"");
    }

    @Test
    void oneOrderAtATimeOffersSkipAndShowsTheStep() {
        // given
        FulfilmentForm form = form(List.of("o1", "o2"), offer("Elko-k1", "CPU", 100, true, "o1:i1:200"));
        form.setOrderByOrder(true);
        form.setOrderCountAtStart(3);
        form.setCommittedSuppliers(new java.util.LinkedHashMap<>(Map.of("AB-k2", 902.4)));

        // when
        String html = render(form, factory.forOrders(form, "store-1", false, labels, TODAY, PL));

        // then
        assertThat(html).contains("Zamówienie 2 z 3").contains("Pomiń to zamówienie")
                .contains("formaction=\"/dashboard/orders/fulfilment/skip\"").contains("data-cl-skip")
                .doesNotContain("Zatwierdź i dobierz resztę").contains("Pokażę następne zamówienie")
                .contains("wcześniejsze kroki").contains("902,40")
                .contains("name=\"committedSuppliers[AB-k2]\"");
    }

    @Test
    void theSuperAdminSeesTheStoreAndStoreScopedLinks() {
        // given
        Store store = new Store();
        store.setStoreId("store-1");
        store.setName("Sklep Demo");
        when(storesRepository.findById("store-1")).thenReturn(store);
        FulfilmentForm form = warehouseGroup();

        // when
        String html = render(form, factory.forOrders(form, "store-1", true, labels, TODAY, PL));

        // then
        assertThat(html).contains("Sklep:").contains("Sklep Demo")
                .contains("href=\"/dashboard/store/store-1/orders/w1-a\"")
                .contains("action=\"/dashboard/store/store-1/orders/fulfilment/commit\"");
    }

    @Test
    void aRestockWithoutABudgetHidesProfitAndOffersOneAction() {
        // given
        FulfilmentForm form = new FulfilmentForm("warehouse", "redirect:/dashboard/warehouse?statuses=New",
                new ArrayList<>(List.of(offer("Elko-k1", "Memory", 450, true, ":r1:100000"))));

        // when
        String html = render(form, factory.forRestock(form, labels, false, PL));

        // then
        assertThat(html).contains("Uzupełnij magazyn").contains("href=\"/dashboard/warehouse\"")
                .contains("action=\"/dashboard/warehouse/fulfilment/commit\"")
                .contains("Brak ceny docelowej").contains("Wrócisz do magazynu")
                .doesNotContain("data-cl-coverage").doesNotContain("Zatwierdź i dobierz resztę").doesNotContain("Bez oferty")
                .doesNotContain("??");
    }

    @Test
    void tooltipsAtTheRightEdgeGrowLeftSoTheyNeverWidenThePage() {
        // given
        FulfilmentForm form = new FulfilmentForm("warehouse", "redirect:/dashboard/warehouse?statuses=New",
                new ArrayList<>(List.of(offer("Elko-k1", "Memory", 450, true, ":r1:100000"))));

        // when
        String html = render(form, factory.forRestock(form, labels, false, PL));

        // then
        assertThat(html.split("class=\"cl-profit is-unknown cl-tooltip is-end\"", -1)).hasSize(3);
        assertThat(html).doesNotContain("class=\"cl-profit is-unknown cl-tooltip\"");
    }

    @Test
    void theSummaryAnnouncesOneSentenceInsteadOfEveryNumber() {
        // given
        FulfilmentForm form = warehouseGroup();

        // when
        String html = render(form, factory.forOrders(form, "store-1", false, labels, TODAY, PL));

        // then
        assertThat(html).contains("<p class=\"cl-visually-hidden\" role=\"status\" data-cl-live></p>");
        String summary = html.substring(html.indexOf("id=\"summary\""));
        assertThat(summary.substring(0, summary.indexOf("</dl>"))).doesNotContain("aria-live");
    }

    @Test
    void nothingLeftToOrderShowsTheEmptyStateWithTheWayBack() {
        // given
        FulfilmentForm form = form(List.of("o1"));

        // when
        String html = render(form, factory.forOrders(form, "store-1", false, labels, TODAY, PL));

        // then
        assertThat(html).contains("Pozycje tych zamówień mają już dostawcę").contains("Wróć do kolejki")
                .doesNotContain("data-cl-select ").doesNotContain("Zatwierdź dobór").doesNotContain("??");
    }

    @Test
    void onlyMissingItemsExplainWhyNothingMatched() {
        // given
        FulfilmentForm form = form(List.of("o1"));
        form.setOnlyMultiOrder(true);
        form.setUnmatched(List.of(new UnmatchedItem("o1", "i1", "Mysz", 1, 99, UnmatchedItem.Reason.NARROWED)));

        // when
        String html = render(form, factory.forOrders(form, "store-1", false, labels, TODAY, PL));

        // then
        assertThat(html).contains("Żadna oferta nie pasuje do wybranych zawężeń")
                .contains("przy jednym zamówieniu nic nie zostawia").contains("Mysz").contains("Zatwierdź dobór");
    }

    @Test
    void thePageWithoutJavaScriptSaysItNeedsIt() {
        // given
        FulfilmentForm form = warehouseGroup();

        // when
        String html = render(form, factory.forOrders(form, "store-1", false, labels, TODAY, PL));

        // then
        assertThat(html).contains("<noscript>").contains("Ten ekran wymaga włączonego JavaScriptu.");
    }

    @Test
    void theHeaderLinksTheOrderOnlyWhenThePageHasExactlyOne() {
        // given
        warehouseOrder("o1");
        FulfilmentForm single = form(List.of("o1", "o2"), offer("Elko-k1", "CPU", 100, true, "o1:i1:200"));
        single.setOrderByOrder(true);
        single.setOrderCountAtStart(2);
        FulfilmentForm several = warehouseGroup();

        // when
        String one = render(single, factory.forOrders(single, "store-1", false, labels, TODAY, PL));
        String many = render(several, factory.forOrders(several, "store-1", false, labels, TODAY, PL));

        // then
        assertThat(one).contains("href=\"/dashboard/orders/o1\"").contains("Otwórz zamówienie ").contains("rel=\"noopener\"");
        // the order chips name the order in their link titles; only the header is checked here
        String header = content(many).substring(0, content(many).indexOf("</header>"));
        assertThat(header).doesNotContain("Otwórz zamówienie");
    }

    private static String row(String html, String provider) {
        String row = html.substring(html.indexOf("data-provider=\"" + provider + "\""));
        return row.substring(0, row.indexOf("</tr>"));
    }

    @Test
    void everyOfferIsToggledByALabelWrappingItsCheckboxInTheLastCell() {
        // given
        FulfilmentForm form = warehouseGroup();

        // when
        String html = render(form, factory.forOrders(form, "store-1", false, labels, TODAY, PL));

        // then
        String dear = row(html, "AB-k2");
        String lastCell = dear.substring(dear.lastIndexOf("<td"));
        assertThat(lastCell).contains("<label class=\"cl-offer-toggle\">").contains("data-cl-offer-check")
                .contains("Zamawiam").contains("Zamów").contains("Zamów tę").contains("Zaznaczona");
        assertThat(lastCell.indexOf("<label")).isLessThan(lastCell.indexOf("data-cl-offer-check"));
        assertThat(lastCell.indexOf("data-cl-offer-check")).isLessThan(lastCell.indexOf("</label>"));
        assertThat(html).doesNotContain("cl-table-check").doesNotContain("data-cl-select-all");
    }

    @Test
    void theToggleIsNamedByItsVisibleTextThenTheOfferAndSupplier() {
        // given
        FulfilmentForm form = warehouseGroup();

        // when
        String html = render(form, factory.forOrders(form, "store-1", false, labels, TODAY, PL));

        // then -- WCAG 2.5.3: the accessible name starts with the text on the button
        String cheap = row(html, "Elko-k1");
        assertThat(cheap).contains("aria-labelledby=\"offers-category-1-o0-on offers-category-1-o0-name\"")
                .contains("id=\"offers-category-1-o0-on\"").contains("id=\"offers-category-1-o0-off\"")
                .contains("id=\"offers-category-1-o0-alt\"").contains("id=\"offers-category-1-o0-kept\"")
                .contains("<span class=\"cl-visually-hidden\" id=\"offers-category-1-o0-name\">")
                .doesNotContain("aria-label=\"Zamów: ");
        assertThat(row(html, "AB-k2")).contains("aria-labelledby=\"offers-category-1-o1-off offers-category-1-o1-name\"");
    }

    @Test
    void theHeaderRowIsForScreenReadersAndTheCellsCarryTheirUnits() {
        // given
        FulfilmentForm form = warehouseGroup();

        // when
        String html = render(form, factory.forOrders(form, "store-1", false, labels, TODAY, PL));

        // then
        assertThat(html).contains("<thead class=\"cl-visually-hidden\">");
        String cheap = row(html, "Elko-k1");
        assertThat(cheap).contains("szt.").contains("zł / szt.").contains("brutto 123,00").contains("100,00");
        assertThat(cheap).doesNotContain("data-label=");
    }

    @Test
    void onlyTheFirstCategoryStartsExpandedAndTheMissingGroupAlwaysShows() {
        // given
        FulfilmentForm form = warehouseGroup();

        // when
        String html = render(form, factory.forOrders(form, "store-1", false, labels, TODAY, PL));

        // then
        String toggles = html.substring(html.indexOf("data-cl-offers"));
        assertThat(toggles.split("aria-expanded=", -1)).hasSize(3);
        assertThat(toggles.indexOf("aria-expanded=\"true\"")).isLessThan(toggles.indexOf("aria-expanded=\"false\""));
        String missing = html.substring(html.indexOf("cl-offers-missing"));
        assertThat(missing).contains("Bez oferty").contains("fa-exclamation-triangle")
                .doesNotContain("aria-expanded").doesNotContain("cl-group-toggle");
    }

    @Test
    void theListBarExpandsCollapsesAndTicksTheVisibleOffers() {
        // given
        FulfilmentForm form = warehouseGroup();

        // when
        String html = render(form, factory.forOrders(form, "store-1", false, labels, TODAY, PL));

        // then
        String bar = html.substring(html.indexOf("class=\"cl-list-bar\""), html.indexOf("data-cl-offers"));
        assertThat(bar).contains("data-cl-select-visible").contains("Zaznacz widoczne")
                .contains("data-cl-expand-all").contains("Rozwiń wszystkie")
                .contains("data-cl-collapse-all").contains("Zwiń wszystkie");
        assertThat(html).contains("data-select-visible=\"Zaznacz widoczne\"").contains("data-clear-all=\"Odznacz wszystkie\"")
                .contains("data-clear-matching=\"Odznacz pasujące do filtra\"");
    }

    @Test
    void orderChipsLinkTheOrderAndFilterWithAProgressBar() {
        // given
        FulfilmentForm form = warehouseGroup();

        // when
        String html = render(form, factory.forOrders(form, "store-1", false, labels, TODAY, PL));

        // then
        String chip = html.substring(html.indexOf("class=\"cl-order-chip\""));
        chip = chip.substring(0, chip.indexOf("</button>"));
        assertThat(chip).contains("href=\"/dashboard/orders/w1-a\"").contains("target=\"_blank\"").contains("rel=\"noopener\"")
                .contains("aria-pressed=\"false\"").contains("data-cl-coverage")
                .contains("aria-label=\"Pokaż tylko oferty zamówienia ")
                .contains(": 0/2 pozycji ma dostawcę\"")
                .contains("cl-order-chip-bar").contains("data-cl-coverage-fill")
                .contains("data-cl-coverage-count").contains("0/2");
        assertThat(chip.indexOf("</a>")).isLessThan(chip.indexOf("<button"));
        assertThat(html).doesNotContain("cl-coverage-legend").doesNotContain("cl-chip cl-coverage-chip");
    }

    @Test
    void supplierPillsAreSolidWithATruckOrTheWarehouseIcon() {
        // given
        warehouseOrder("w1-a");
        FulfilmentForm form = form(List.of("w1-a"), offer("Elko-k1", "CPU", 100, true, "w1-a:i1:200"),
                offer("Warehouse", "CPU", 90, false, "w1-a:i1:200"));

        // when
        String html = render(form, factory.forOrders(form, "store-1", false, labels, TODAY, PL));

        // then
        assertThat(row(html, "Elko-k1")).contains("class=\"cl-supplier-pill is-c1\"").contains("fa-truck");
        assertThat(row(html, "Warehouse")).contains("class=\"cl-supplier-pill is-c0\"").contains("fa-warehouse")
                .doesNotContain("fa-truck");
        assertThat(html).doesNotContain("cl-supplier-dot");
    }

    @Test
    void theOrdersOfAnOfferReadAsNumberQuantityAndPrice() {
        // given
        FulfilmentForm form = warehouseGroup();

        // when
        String html = render(form, factory.forOrders(form, "store-1", false, labels, TODAY, PL));

        // then
        String gpu = html.substring(html.indexOf("data-category=\"GPU\""));
        gpu = gpu.substring(0, gpu.indexOf("</tr>"));
        assertThat(gpu).contains("data-cl-alloc").contains("×1").contains("500,00")
                .contains("href=\"/dashboard/orders/w2-b\"");
    }
}
