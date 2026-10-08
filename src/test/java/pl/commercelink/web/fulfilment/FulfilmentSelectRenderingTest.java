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
        assertThat(content(many)).doesNotContain("Otwórz zamówienie");
    }
}
