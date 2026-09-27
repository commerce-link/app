package pl.commercelink.web;

import org.springframework.context.support.ResourceBundleMessageSource;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import pl.commercelink.documents.Document;
import pl.commercelink.documents.DocumentType;
import pl.commercelink.inventory.deliveries.DeliveryRedirectResolver;
import pl.commercelink.inventory.deliveries.DropshipItemLookup;
import pl.commercelink.inventory.supplier.SupplierChoice;
import pl.commercelink.inventory.supplier.SupplierLabels;
import pl.commercelink.orders.BillingDetails;
import pl.commercelink.orders.FulfilmentStatus;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.orders.OrderReview;
import pl.commercelink.orders.OrderReviewStatus;
import pl.commercelink.orders.OrderStatus;
import pl.commercelink.orders.Payment;
import pl.commercelink.orders.PaymentSource;
import pl.commercelink.orders.PositionGroup;
import pl.commercelink.orders.Shipment;
import pl.commercelink.orders.ShipmentCarrierOptions;
import pl.commercelink.orders.ShipmentTrackingStatus;
import pl.commercelink.orders.ShipmentType;
import pl.commercelink.orders.ShippingDetails;
import pl.commercelink.orders.event.OrderEventsRepository;
import pl.commercelink.orders.fulfilment.FulfilmentType;
import pl.commercelink.products.ProductCatalogRepository;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.taxonomy.TaxonomyCache;
import pl.commercelink.web.orders.OrderPageModel;
import pl.commercelink.web.orders.OrderPageModelFactory;
import pl.commercelink.web.orders.OrderLabels;
import pl.commercelink.web.orders.OrderSettingsView;
import pl.commercelink.web.settings.SettingsTemplateRenderer;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** The order details page as the controller renders it (Polish messages, real layout). */
class OrderDetailsTemplateTest {

    static final Locale PL = Locale.forLanguageTag("pl");

    static OrderPageModelFactory factory(Set<String> dropshipItemIds) {
        return factory(dropshipItemIds, false);
    }

    static OrderPageModelFactory factory(Set<String> dropshipItemIds, boolean documentsGenerationEnabled) {
        StoresRepository stores = mock(StoresRepository.class);
        Store store = new Store();
        store.setStoreId("store-1");
        store.setName("Demo");
        if (documentsGenerationEnabled) {
            pl.commercelink.stores.WarehouseConfiguration warehouse = new pl.commercelink.stores.WarehouseConfiguration();
            warehouse.setDocumentsGenerationEnabled(true);
            store.setWarehouseConfiguration(warehouse);
        }
        when(stores.findById("store-1")).thenReturn(store);
        SupplierLabels labels = mock(SupplierLabels.class);
        when(labels.forStore(any())).thenReturn(new SupplierLabels(mock(StoresRepository.class)).forStore(null));
        DropshipItemLookup dropship = mock(DropshipItemLookup.class);
        when(dropship.itemIdsInDropshipDeliveries(anyString(), any())).thenReturn(dropshipItemIds);
        OrderEventsRepository events = mock(OrderEventsRepository.class);
        when(events.findByOrderId(anyString())).thenReturn(List.of());
        ShipmentCarrierOptions carrierOptions = mock(ShipmentCarrierOptions.class);
        when(carrierOptions.forOrder(any(), any())).thenReturn(List.of("DPD", "InPost"));
        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding("UTF-8");
        messages.setFallbackToSystemLocale(false);
        OrderPageModelFactory factory = new OrderPageModelFactory(stores, events, dropship, new DeliveryRedirectResolver(),
                labels, carrierOptions, mock(ProductCatalogRepository.class), mock(TaxonomyCache.class), messages);
        ReflectionTestUtils.setField(factory, "appDomain", "https://app.example");
        return factory;
    }

    static Order order(OrderStatus status) {
        Order order = new Order("store-1");
        order.setOrderId("3e373abc-1111-2222-3333-444455556666");
        order.setStatus(status);
        order.setFulfilmentType(FulfilmentType.WarehouseFulfilment);
        order.setTotalPrice(2 * 749 + 299);
        order.setReview(new OrderReview(OrderReviewStatus.ToBeCollected));
        BillingDetails billing = new BillingDetails();
        billing.setName("Piotr");
        billing.setSurname("Wiśniewski");
        billing.setEmail("piotr@example.pl");
        order.setBillingDetails(billing);
        order.addShipment(new Shipment(ShipmentType.Courier));
        return order;
    }

    static Order b2bOrder(OrderStatus status) {
        Order order = order(status);
        order.getBillingDetails().setTaxId("5250000000");
        order.getBillingDetails().setCompanyName("Firma Sp. z o.o.");
        return order;
    }

    static List<OrderItem> items(Order order) {
        OrderItem cpu = new OrderItem(order.getOrderId(), "CPU", "AMD Ryzen 7 9800X3D", 2, 749, "100-100001084WOF", false, 0);
        cpu.setStatus(FulfilmentStatus.New);
        cpu.setManufacturerCode("100-100001084WOF");
        cpu.setCost(579);
        OrderItem service = new OrderItem(order.getOrderId(), "Usługi", "Montaż i testy zestawu", 1, 299, "SRV", false,
                PositionGroup.SERVICE_GROUP_START);
        service.setService(true);
        service.setStatus(FulfilmentStatus.Delivered);
        return List.of(cpu, service);
    }

    static String render(Order order, List<OrderItem> items, OrderPageModelFactory.Viewer viewer, Set<String> dropship) {
        return render(order, items, viewer, dropship, false);
    }

    static String render(Order order, List<OrderItem> items, OrderPageModelFactory.Viewer viewer, Set<String> dropship,
                         boolean documentsGenerationEnabled) {
        OrderPageModel page = factory(dropship, documentsGenerationEnabled).build(order, items, viewer, PL);
        Map<String, Object> variables = new HashMap<>();
        variables.put("navigation", null);
        variables.put("page", page);
        variables.put("order", order);
        variables.put("orderId", order.getOrderId());
        variables.put("settings", page.settings());
        return SettingsTemplateRenderer.render("orders/details", variables);
    }

    static String render(Order order, OrderPageModelFactory.Viewer viewer) {
        return render(order, items(order), viewer, Set.of());
    }

    static final OrderPageModelFactory.Viewer ADMIN = new OrderPageModelFactory.Viewer(false, true, null);
    static final OrderPageModelFactory.Viewer USER = new OrderPageModelFactory.Viewer(false, false, null);
    static final OrderPageModelFactory.Viewer SUPER_ADMIN = new OrderPageModelFactory.Viewer(true, false, null);

    /** Cuts before the layout tail (</main>), which carries the old delete/save modals (onclick=, class="button"). */
    static String page(String html) {
        int start = html.indexOf("<section class=\"cl-page\"");
        return html.substring(start, html.indexOf("</main>", start));
    }

    static int occurrences(String html, String needle) {
        return html.split(Pattern.quote(needle), -1).length - 1;
    }

    @Test
    void oneHeadingTheStatusBesideItAndTheCardsInReadingOrder() {
        // when
        String html = page(render(order(OrderStatus.Assembly), ADMIN));

        // then
        assertThat(occurrences(html, "<h1")).isEqualTo(1);
        assertThat(html).contains("Zamówienie 3e373abc").contains("cl-status is-info").contains("W kompletacji")
                .contains("data-cl-dialog-open=\"status-dialog\"").contains("Piotr Wiśniewski").contains("1 797,00 PLN")
                .doesNotContain("??");
        int closing = html.indexOf("id=\"closing-title\"");
        int items = html.indexOf("id=\"pozycje\"");
        int customer = html.indexOf("id=\"customer-title\"");
        int shipments = html.indexOf("id=\"przesylki\"");
        assertThat(closing).isPositive().isLessThan(items);
        assertThat(items).isLessThan(shipments);
        assertThat(html).doesNotContain("data-order=").contains("data-cl-collapse");
    }

    @Test
    void theCardsFollowOneReadingOrderInTheHtmlSoPhonesNeedNoReordering() {
        // when
        String html = page(render(order(OrderStatus.Assembly), ADMIN));

        // then: main column first, side column after; the stylesheet stacks them in this same order below 1366 px
        List<Integer> positions = List.of(html.indexOf("id=\"items-title\""), html.indexOf("id=\"shipments-title\""),
                html.indexOf("id=\"documents-title\""), html.indexOf("id=\"payments-title\""),
                html.indexOf("id=\"customer-title\""), html.indexOf("id=\"settings-title\""),
                html.indexOf("id=\"finances-title\""), html.indexOf("id=\"history-title\""));
        assertThat(positions).doesNotContain(-1).isSorted();
    }

    @Test
    void theItemsTableHasFiveColumnsAndServicesInTheirOwnGroup() {
        // when
        String html = page(render(order(OrderStatus.New), ADMIN));

        // then
        assertThat(html).contains("<col class=\"cl-col-check\">").contains("<col class=\"cl-col-menu\">")
                .contains("class=\"cl-table-group\"").contains("Usługi i dostawa")
                .contains("2 × 749,00").contains("koszt 579,00 netto")
                .contains("data-cl-copy=\"100-100001084WOF\"")
                .containsPattern("data-ready-for-allocation=\"false\"[^>]*data-removable=\"true\"")
                .contains("name=\"orderItems[0].selected\"").contains("name=\"orderItems[0].itemId\"");
    }

    @Test
    void unavailableMenuEntriesStayVisibleWithTheirReason() {
        // given
        Order order = order(OrderStatus.New);
        OrderItem allocated = inDelivery(order, "Acme", FulfilmentStatus.Allocation);

        // when
        String html = page(render(order, ADMIN));
        String withSupplier = page(render(order, List.of(allocated), ADMIN, Set.of()));

        // then: "Split set" is absent for an item that is not a set, not greyed out
        assertThat(html).containsPattern("aria-disabled=\"true\">\\s*<span>Usuń dostawcę</span>\\s*<span class=\"cl-menu-reason\">Pozycja nie ma dostawcy</span>")
                .doesNotContain("<span>Podziel zestaw</span>").doesNotContain("data-cl-dialog-open=\"split-group-dialog\"")
                .contains("data-cl-dialog-open=\"assign-sku-dialog\"").contains("data-cl-dialog-open=\"assign-supplier-dialog\"");
        assertThat(withSupplier).contains("/clear-supplier?itemId=").contains("data-cl-confirm")
                .containsPattern("<span>Przypisz dostawcę</span>\\s*<span class=\"cl-menu-reason\">Najpierw usuń obecnego dostawcę</span>");
    }

    /**
     * Without JavaScript a button toggling a hidden list opens nothing, so prints, the item history, cancelling,
     * the row actions and issuing documents were unreachable. A native details opens by itself; a dialog opener
     * inside it keeps an href to a page doing the same.
     */
    @Test
    void everyMenuIsANativeDetailsAndEveryDialogOpenerInItHasAPageToFallBackOn() {
        // when
        String html = page(render(b2bOrder(OrderStatus.Realization), ADMIN));

        // then
        java.util.regex.Matcher menus = Pattern.compile("<(\\w+)[^>]*class=\"cl-menu\"").matcher(html);
        int count = 0;
        while (menus.find()) {
            assertThat(menus.group(1)).as(menus.group()).isEqualTo("details");
            count++;
        }
        assertThat(count).as("Więcej, Wystaw and a row menu per item").isGreaterThanOrEqualTo(4);
        assertThat(html).doesNotContain("aria-controls=\"order-more-menu\"").doesNotContainPattern("class=\"cl-menu-list\"[^>]*hidden")
                .containsPattern("<summary class=\"cl-button is-icon\" aria-label=\"[^\"]+\"");
        int openers = 0;
        for (String menu : html.split("<details class=\"cl-menu\"")) {
            String body = menu.contains("</details>") ? menu.substring(0, menu.indexOf("</details>")) : "";
            java.util.regex.Matcher opener = Pattern.compile("<\\w+[^>]*data-cl-dialog-open=[^>]*>").matcher(body);
            while (opener.find()) {
                assertThat(opener.group()).as(opener.group()).startsWith("<a ").contains(" href=\"/dashboard/orders/");
                openers++;
            }
        }
        assertThat(openers).as("assign SKU/supplier/warehouse and the invoice types").isGreaterThanOrEqualTo(3);
        assertThat(html).containsPattern("<a class=\"cl-menu-item\" data-cl-dialog-open=\"issue-dialog\"\\s+href=\"/dashboard/orders/[^\"]+/invoicing\\?documentType=InvoiceVat\"");
    }

    @Test
    void theInvoiceConfirmationPageAsksTheDialogsQuestionWithTheSameCheckbox() {
        // given
        java.util.Map<String, Object> variables = new java.util.HashMap<>();
        variables.put("orderId", "3e373abc-1111-2222-3333-444455556666");
        variables.put("documentType", "InvoiceVat");
        variables.put("documentLabelKey", "DocumentType.InvoiceVat");
        variables.put("backLabel", "Zamówienie 3e373abc");

        // when
        String html = page(SettingsTemplateRenderer.render("orders/invoicing-confirm", variables));

        // then
        assertThat(html).contains("Wystawić dokument: Faktura VAT?")
                .contains("action=\"/dashboard/orders/3e373abc-1111-2222-3333-444455556666/invoicing\"")
                .containsPattern("<input type=\"hidden\" name=\"documentType\" value=\"InvoiceVat\">")
                .containsPattern("<input type=\"checkbox\" name=\"send\" value=\"true\">")
                .contains("Wyślij do klienta").contains("Wystaw dokument")
                .doesNotContain("style=").doesNotContain("onclick=");
    }

    @Test
    void menuSeparatorsAreHiddenFromAssistiveTechnologySoTheListHoldsOnlyListItems() {
        // when
        String html = page(render(order(OrderStatus.New), ADMIN));

        // then (E2E axe "list": an li with role="separator" is not a list item)
        assertThat(html).contains("class=\"cl-menu-sep\" aria-hidden=\"true\"").doesNotContain("role=\"separator\"");
    }

    @Test
    void aUserReceivesNoCostOrProfitInTheHtml() {
        // when
        String user = page(render(order(OrderStatus.New), USER));
        String admin = page(render(order(OrderStatus.New), ADMIN));

        // then
        assertThat(user).doesNotContain("koszt 579").doesNotContain("Zysk (z VAT)").doesNotContain("Koszt produktów");
        assertThat(admin).contains("Zysk (z VAT)").contains("Koszt produktów (brutto)");
    }

    @Test
    void costAndProfitAreACollapsedSectionOfTheFinancesCardForAnAdminOnly() {
        // when
        String user = page(render(order(OrderStatus.New), USER));
        String admin = page(render(order(OrderStatus.New), ADMIN));

        // then
        java.util.regex.Matcher costs = Pattern.compile("(?s)<details class=\"cl-disclosure\" id=\"finances-costs\">(.*?)</details>")
                .matcher(admin);
        assertThat(costs.find()).isTrue();
        assertThat(costs.group(1)).contains("<summary>Koszt i zysk</summary>").contains("Zysk (bez VAT)");
        assertThat(user).doesNotContain("finances-costs").doesNotContain("cl-kv-group");
    }

    @Test
    void superAdminPageCarriesNoFormDialogOrMenu() {
        // when
        String html = page(render(order(OrderStatus.Assembly), SUPER_ADMIN));

        // then
        assertThat(html).contains("tylko do odczytu").contains("href=\"/dashboard/store/store-1/orders/3e373abc-1111-2222-3333-444455556666/card\"");
        assertThat(html).doesNotContain("method=\"post\"").doesNotContain("data-cl-dialog-open")
                .doesNotContain("cl-table-check").doesNotContain("item-menu-").doesNotContain("data-cl-confirm")
                .doesNotContain("class=\"cl-back\"").doesNotContain("Anuluj zamówienie")
                .doesNotContain("Edytuj dane rozliczeniowe").doesNotContain("Edytuj adres wysyłki")
                .doesNotContain("settings-dialog");
    }

    @Test
    void superAdminSeesAReadOnlyPage() {
        // when
        String html = page(render(order(OrderStatus.Delivered), SUPER_ADMIN));

        // then
        assertThat(html).doesNotContain("data-cl-select-row").doesNotContain("cl-menu-list\" id=\"item-")
                .doesNotContain("status-dialog").doesNotContain("settings-dialog").doesNotContain("data-cl-dialog-open")
                .contains("/dashboard/store/store-1/orders/").contains("tylko do odczytu");
    }

    @Test
    void skuAppearsOnlyOnNewRows() {
        // given
        Order order = order(OrderStatus.Realization);
        OrderItem fresh = new OrderItem(order.getOrderId(), "CPU", "AMD Ryzen 5", 1, 599, "A:B", false, 0);
        fresh.setStatus(FulfilmentStatus.New);
        fresh.setManufacturerCode("MFN-1");
        OrderItem delivered = new OrderItem(order.getOrderId(), "GPU", "RTX 5070", 1, 2999, "C:D", false, 1);
        delivered.setStatus(FulfilmentStatus.Delivered);
        delivered.setManufacturerCode("MFN-2");

        // when
        String html = page(render(order, List.of(fresh, delivered), ADMIN, Set.of()));

        // then
        assertThat(html).contains("A:B").doesNotContain("C:D");
    }

    @Test
    void anOrderWithoutAddressesSourceOrReviewRenders() {
        // given
        Order bare = new Order("store-1");
        bare.setOrderId("0badf00d-1111-2222-3333-444455556666");
        bare.setStatus(OrderStatus.Delivered);

        // when
        String html = page(render(bare, List.of(), ADMIN, Set.of()));

        // then
        assertThat(html).contains("cl-closing").contains("id=\"customer-title\"").contains(">Klient<").doesNotContain("??");
    }

    @Test
    void theShipmentsDialogHasOneRowPerShipmentAndNoAutomaticBlankOne() {
        // given
        Order order = order(OrderStatus.Realization);
        order.getShipments().get(0).setCarrier("DPD");

        // when
        String html = page(render(order, ADMIN));

        // then
        assertThat(html).contains("id=\"shipments-dialog\"").contains("name=\"shipments[0].type\"")
                .doesNotContain("name=\"shipments[1].type\"").contains("data-cl-shipment-template")
                .contains("data-cl-carrier-select").contains("value=\"__other__\"")
                .containsPattern("<option[^>]*value=\"Courier\"[^>]*selected[^>]*>Kurier</option>")
                .contains("name=\"shipments[0].trackingUrl\"");
    }

    @Test
    void theShipmentsDialogRendersOneBlankRowWhenTheOrderHasNoShipments() {
        // given: a blank row lets the operator fill in the first shipment; "Save" posting nothing must not be silent
        Order order = order(OrderStatus.Realization);
        order.setShipments(List.of());

        // when
        String html = page(render(order, ADMIN));

        // then: exactly one row, no automatic extra one next to it
        assertThat(html).contains("id=\"shipments-dialog\"").contains("name=\"shipments[0].type\"")
                .doesNotContain("name=\"shipments[1].type\"");
    }

    @Test
    void theItemDialogsAreOnThePageAndTheMoveFieldBelongsToTheItemsForm() {
        // when
        String html = page(render(order(OrderStatus.New), ADMIN));

        // then
        assertThat(html).contains("id=\"assign-sku-dialog\"").contains("id=\"assign-supplier-dialog\"")
                .contains("id=\"assign-warehouse-dialog\"").contains("id=\"split-group-dialog\"")
                .contains("id=\"move-dialog\"").contains("class=\"cl-dialog is-form")
                .containsPattern("<input[^>]*name=\"targetOrderId\"[^>]*form=\"order-items-form\"")
                .contains("data-cl-custom-option=\"__custom__\"").contains("data-cl-cost-type")
                .contains("/move-target");
        assertThat(html).containsPattern("<script>\\s*window\\.CL_SPLIT_PREVIEWS = ");
        assertThat(render(order(OrderStatus.New), ADMIN)).contains("/js/order-item-dialogs.js").contains("/js/supplier-choice.js");
    }

    @Test
    void withoutJavascriptExactlyOneEnabledTargetOrderIdFieldReachesTheServer() {
        // when: the move dialog's own field and items.html's <noscript> fallback both post to #order-items-form
        String html = page(render(order(OrderStatus.New), ADMIN));

        // then: the dialog's field starts disabled (order-item-dialogs.js enables it once it runs), so a browser
        // without JavaScript never parses the <noscript> fallback's field away and submits it alone -- exactly
        // one targetOrderId field reaches the server either way
        assertThat(html).containsPattern("<input[^>]*id=\"move-target\"[^>]*disabled[^>]*>");
        assertThat(html).containsPattern("<input[^>]*id=\"move-target-fallback\"[^>]*name=\"targetOrderId\"[^>]*>");
        assertThat(html).doesNotContainPattern("<input[^>]*id=\"move-target-fallback\"[^>]*disabled[^>]*>");
    }

    @Test
    void aDeliveredOrderWithSerialItemsRendersTheSerialNumbersDialog() {
        // given: canAddSerials() needs a non-New order with a Delivered product item
        Order order = order(OrderStatus.Delivered);
        OrderItem cpu = new OrderItem(order.getOrderId(), "CPU", "AMD Ryzen 7 9800X3D", 1, 749, "100-100001084WOF", false, 0);
        cpu.setStatus(FulfilmentStatus.Delivered);
        cpu.setManufacturerCode("100-100001084WOF");

        // when
        String html = page(render(order, List.of(cpu), ADMIN, Set.of()));

        // then
        assertThat(html).contains("id=\"serials-dialog\"")
                .containsPattern("<input[^>]*name=\"orderItems\\[0\\]\\.serialNo\"")
                .containsPattern("<input[^>]*type=\"hidden\"[^>]*name=\"orderItems\\[0\\]\\.itemId\"");
    }

    @Test
    void theItemsTableCarriesTheSelectTableHookWhenItemsAreSelectable() {
        // when: table-select.js only wires up table[data-cl-select-table]; a th:attr with an empty-string true
        // value is dropped by Thymeleaf, so the value must not be the empty string
        String html = page(render(order(OrderStatus.New), ADMIN));

        // then
        assertThat(html).contains("data-cl-select-table");
    }

    @Test
    void aCompletedOrderShowsItsSettingsReadOnlyAndNoClosingStrip() {
        // when
        String html = page(render(order(OrderStatus.Completed), ADMIN));

        // then
        assertThat(html).doesNotContain("id=\"closing-title\"").doesNotContain("cl-closing")
                .doesNotContain("id=\"order-settings-form\"").doesNotContain("settings-dialog")
                .contains("id=\"settings-title\"").contains("class=\"cl-kv is-column\"")
                .contains("Zakończone automatycznie po rozliczeniu")
                .doesNotContain("data-cl-dialog-open=\"status-dialog\"");
    }

    @Test
    void anOpenOrderEditsItsSettingsInADialogThatClosesOnSuccess() {
        // when
        String html = page(render(order(OrderStatus.Assembly), ADMIN));

        // then
        assertThat(html).containsPattern("href=\"/dashboard/orders/3e373abc-1111-2222-3333-444455556666/settings\"[^>]*data-cl-dialog-open=\"settings-dialog\"")
                .contains("id=\"settings-dialog\"").contains("id=\"order-settings-form\"")
                .contains("action=\"/dashboard/orders/3e373abc-1111-2222-3333-444455556666/updateOrderInfo\"")
                .contains("data-cl-dialog-close-on-success=\"true\"").contains("data-cl-async")
                .containsPattern("<section class=\"cl-card\" aria-labelledby=\"settings-title\" data-cl-collapse");
        assertThat(html.indexOf("id=\"settings-title\"")).isLessThan(html.indexOf("id=\"settings-dialog\""));
    }

    @Test
    void aClosedOrderLinksEachItemNameToItsReadOnlyItemPage() {
        // given
        Order completed = order(OrderStatus.Completed);
        List<OrderItem> items = items(completed);
        String itemPage = "href=\"/dashboard/orders/3e373abc-1111-2222-3333-444455556666/items/" + items.get(0).getItemId() + "\"";

        // when
        String closed = page(render(completed, items, ADMIN, Set.of()));
        String superAdmin = page(render(completed, items, SUPER_ADMIN, Set.of()));
        String open = page(render(order(OrderStatus.Assembly), ADMIN));

        // then
        assertThat(closed).containsPattern("<a class=\"cl-table-link\" " + Pattern.quote(itemPage) + ">AMD Ryzen 7 9800X3D</a>");
        assertThat(superAdmin).doesNotContain("class=\"cl-table-link\" href=\"/dashboard/orders/");
        assertThat(open).doesNotContain(">AMD Ryzen 7 9800X3D</a>");
    }

    @Test
    void theClosedOrderMenuKeepsNoGreyedOutCancelOrDelete() {
        // when
        String completed = page(render(order(OrderStatus.Completed), ADMIN));
        String open = page(render(order(OrderStatus.Assembly), ADMIN));

        // then
        assertThat(completed).doesNotContain("Usunąć można tylko nowe zamówienie");
        assertThat(open).contains("Usunąć można tylko nowe zamówienie");
    }

    @Test
    void theShippingAddressPrintsOnceWhenItMatchesBilling() {
        // given: th:unless and th:replace on the same element would discard the condition and print the
        // shipping block a second time even though it is the same as billing
        Order order = order(OrderStatus.Assembly);
        order.getBillingDetails().setStreetAndNumber("ul. Przykładowa 5");
        order.getBillingDetails().setPostalCode("00-001");
        order.getBillingDetails().setCity("Warszawa");
        ShippingDetails shipping = new ShippingDetails();
        shipping.setName("Piotr");
        shipping.setSurname("Wiśniewski");
        shipping.setStreetAndNumber("ul. Przykładowa 5");
        shipping.setPostalCode("00-001");
        shipping.setCity("Warszawa");
        order.setShippingDetails(shipping);

        // when
        String html = page(render(order, ADMIN));

        // then
        assertThat(html).contains("jak dane rozliczeniowe");
        assertThat(occurrences(html, "ul. Przykładowa 5")).isEqualTo(1);
    }

    @Test
    void theChecklistSaysWhatIsMissingWithLinksToTheCards() {
        // given
        Order order = order(OrderStatus.Assembly);
        order.addDocument(new Document("fv", "FV/2026/09/118", null, DocumentType.InvoiceVat));

        // when
        String html = page(render(order, ADMIN));

        // then
        assertThat(html).contains("class=\"cl-card is-status cl-closing\"").contains("class=\"cl-doc-marks is-sentences\"")
                .contains("<span class=\"cl-doc-mark-icon\" aria-hidden=\"true\">✗</span>")
                .contains("<span class=\"cl-doc-mark-icon\" aria-hidden=\"true\">✓</span>")
                .doesNotContain("fa-times").doesNotContain("fa-check")
                .contains("aria-label=\"Warunki zamknięcia zamówienia\"");
        assertThat(html).contains("Do zamknięcia brakuje:").contains("href=\"#platnosci\"")
                .contains("data-cl-dialog-open=\"review-dialog\"").contains("Do zrobienia:").doesNotContain("brakuje:</span>")
                .contains("Faktura już wystawiona");
    }

    @Test
    void theChecklistMarksWhatDoesNotApplyWithoutATick() {
        // given
        Order order = order(OrderStatus.Assembly);
        order.setShipments(new ArrayList<>());
        order.setReview(null);

        // when
        String html = page(render(order, ADMIN));

        // then
        String strip = html.substring(html.indexOf("class=\"cl-doc-marks is-sentences\""), html.indexOf("</ul>", html.indexOf("cl-doc-marks is-sentences")));
        assertThat(occurrences(strip, "class=\"cl-doc-mark is-na\"")).isEqualTo(2);
        assertThat(occurrences(strip, "<span class=\"cl-doc-mark-icon\" aria-hidden=\"true\">–</span>")).isEqualTo(2);
        assertThat(occurrences(strip, "Nie dotyczy:")).isEqualTo(2);
        assertThat(strip).doesNotContain("✓").contains("Brak przesyłek (odbiór osobisty lub wysyłka poza systemem)")
                .contains("Opinia nie jest zbierana");
    }

    @Test
    void dropshipItemsGreyOutWarehouseMovesAndAddingItems() {
        // given (intent of DropshipTemplateTest#orderDetailsGreysOutWarehouseMoves… and …DisablesAddingItems…)
        Order order = order(OrderStatus.Assembly);
        List<OrderItem> items = items(order);

        // when
        String html = page(render(order, items, ADMIN, Set.of(items.get(0).getItemId())));

        // then
        // th:attr (data-cl-bulk-scope, ...) renders before th:disabled in the output tag regardless of source order
        assertThat(html).containsPattern("data-cl-bulk-scope=\"allocated-product\"[^>]*disabled=\"disabled\"")
                .contains(DROPSHIP_LOCKED);
    }

    @Test
    void thePageCarriesNoInlineStylesOrHandlersAndLoadsItsScripts() {
        // when
        String html = render(order(OrderStatus.Assembly), ADMIN);

        // then
        assertThat(page(html)).doesNotContain("style=").doesNotContain("<style").doesNotContain("onclick=")
                .doesNotContain("is-link\"").doesNotContain("is-hoverable");
        assertThat(html).contains("/js/menu.js").contains("/js/dialog.js").contains("/js/collapse.js")
                .contains("/js/copy-field.js").contains("/js/confirm-dialog.js").contains("/js/async-form.js")
                .contains("/js/table-select.js");
    }

    @Test
    void theSelectionBarCarriesTheScopeTemplateAndThePageLoadsTheItemsScript() {
        // when
        String html = render(order(OrderStatus.New), ADMIN);

        // then
        assertThat(html).contains("data-cl-scope-template=\"{label} ({n} z {m})\"").contains("/js/order-items.js")
                .contains("data-cl-bulk-confirm-message=\"Do alokacji trafią zaznaczone pozycje: {n}.");
    }

    @Test
    void theAddItemsDialogIsOnTheOrderPageForTheOrdersAddress() {
        // when
        String html = render(order(OrderStatus.New), ADMIN);

        // then
        assertThat(html).contains("id=\"item-add-dialog\"")
                .contains("data-add-items-url=\"/dashboard/orders/3e373abc-1111-2222-3333-444455556666/add-items\"");
    }

    @Test
    void theMissingEanMessageAppearsAboveTheItemsCard() {
        // given: order.items.add.error.no.mfn flashes through the shared errorMessage banner, which the layout
        // renders once, above <main>, so it lands above every card on this page
        Order order = order(OrderStatus.New);
        OrderPageModel page = factory(Set.of()).build(order, items(order), ADMIN, PL);
        Map<String, Object> variables = new HashMap<>();
        variables.put("navigation", null);
        variables.put("page", page);
        variables.put("order", order);
        variables.put("orderId", order.getOrderId());
        variables.put("settings", page.settings());
        variables.put("errorMessage",
                "Pozycja z EAN EAN-1 nie ma kodu producenta ani ofert — dodaj ją z cennika albo wpisz kod producenta.");

        // when
        String html = SettingsTemplateRenderer.render("orders/details", variables);

        // then
        int errorAt = html.indexOf("Pozycja z EAN EAN-1");
        int itemsCardAt = html.indexOf("id=\"pozycje\"");
        assertThat(errorAt).isNotNegative();
        assertThat(itemsCardAt).isNotNegative();
        assertThat(errorAt).isLessThan(itemsCardAt);
    }

    static final String DROPSHIP_LOCKED = ResourceBundle.getBundle("messages", PL).getString("order.items.action.dropship.locked");

    @Test
    void theStatusDialogOffersTheManualStatusesWithTheirEffectAndABlockedDelivered() {
        // given
        Order order = order(OrderStatus.Assembly);
        order.setEmailNotificationsEnabled(true);

        // when
        String html = page(render(order, ADMIN));

        // then
        assertThat(html).contains("id=\"status-dialog\"").contains("action=\"/dashboard/orders/3e373abc-1111-2222-3333-444455556666/status\"")
                .containsPattern("name=\"status\" value=\"Assembly\" checked")
                .contains("W kompletacji (obecny)").contains("Towar zamówiony, w drodze")
                .containsPattern("name=\"status\" value=\"Delivered\" disabled")
                .contains("Niedostępne: przesyłki nie mają danych nadania")
                .contains("Klient może dostać e-mail o zmianie statusu.");
        assertThat(html.indexOf("Klient może dostać e-mail")).isLessThan(html.indexOf("value=\"New\""));
    }

    @Test
    void theDocumentDialogsCarryTheOldFieldsWithTypesInWords() {
        // when
        String b2c = page(render(order(OrderStatus.Realization), ADMIN));
        String b2b = page(render(b2bOrder(OrderStatus.Realization), ADMIN));

        // then
        assertThat(b2c).contains("id=\"document-dialog\"").contains("name=\"number\"").contains("name=\"link\"")
                .contains("name=\"issuedAt\"").containsPattern("<option value=\"Receipt\" selected=\"selected\">Paragon</option>")
                .contains("<option value=\"InvoicePersonal\">Faktura imienna</option>")
                .doesNotContain(">Receipt<");
        assertThat(b2b).contains("id=\"issue-dialog\"").contains("name=\"documentType\"")
                .contains("name=\"send\"").contains("Wyślij do klienta")
                .contains("data-document-type=\"InvoiceVat\"");
    }

    @Test
    void theReviewCanBeEditedEvenWhenTheOrderHasNoneYet() {
        // given
        Order order = order(OrderStatus.Delivered);
        order.setReview(null);

        // when
        String html = page(render(order, ADMIN));

        // then
        assertThat(html).contains("Opinia:").contains("nie jest zbierana").contains("data-cl-dialog-open=\"review-dialog\"")
                .contains("id=\"review-dialog\"").contains("name=\"review.status\"").contains("name=\"review.requestedAt\"")
                .contains(">Do zebrania</option>").doesNotContain(">ToBeCollected<");
    }

    @Test
    void thePaymentDialogsPostToTheOrderAndTheEditRowsCanBeRemoved() {
        // given
        Order order = order(OrderStatus.Realization);
        order.addPayment(new Payment("REF-1", "Jan Kowalski", PaymentSource.BankTransfer, 500, 2));

        // when
        String html = page(render(order, ADMIN));

        // then
        assertThat(html).contains("id=\"addPaymentModal\"")
                .contains("action=\"/dashboard/orders/3e373abc-1111-2222-3333-444455556666/addPayment\"")
                .contains("id=\"payments-edit-dialog\"").contains("name=\"payments[0].amount\"")
                .contains("name=\"payments[0].direction\"").contains("data-cl-payment-remove")
                .contains(">Przelew bankowy</option>").doesNotContain("addEmptyPaymentRow");
    }

    static OrderItem inDelivery(Order order, String deliveryId, FulfilmentStatus status) {
        OrderItem item = new OrderItem(order.getOrderId(), "GPU", "RTX 5070 Ti", 1, 3999, "SKU-G", false, 1);
        item.setDeliveryId(deliveryId);
        item.setStatus(status);
        item.setManufacturerCode("GV-N507T");
        return item;
    }

    @Test
    void theSupplierColumnLinksToTheDeliveryTheResolverPicksForTheOrder() {
        // given (was DropshipTemplateTest#orderDetailsResolvesTheDeliveryLinkWithTheOrder): the order-aware
        // overload (resolveFor(Order, OrderItem)) must be used, not resolveFor(OrderItem) alone. The two only
        // diverge for a DirectToConsumer order with an item still awaiting delivery (New/Allocation, unclaimed) —
        // there the order-aware resolver sends the operator to the dropship confirmation page instead of
        // "create a warehouse delivery".
        Order order = order(OrderStatus.Assembly);
        order.setFulfilmentType(FulfilmentType.DirectToConsumer);
        OrderItem awaiting = inDelivery(order, "Acme", FulfilmentStatus.Allocation);
        DeliveryRedirectResolver resolver = new DeliveryRedirectResolver();
        String expected = resolver.resolveFor(order, awaiting);
        String itemOnly = resolver.resolveFor(awaiting);
        // the fixture is only useful if the two overloads actually disagree
        assertThat(expected).contains("/dropship?provider=");
        assertThat(expected).isNotEqualTo(itemOnly);

        // when
        String html = page(render(order, List.of(awaiting), ADMIN, Set.of()));

        // then: the rendered link is the order-aware one, not the item-only fallback a regression would produce
        assertThat(html).contains("class=\"cl-table-link\"").contains("href=\"" + expected.replace("&", "&amp;") + "\"")
                .doesNotContain("href=\"" + itemOnly.replace("&", "&amp;") + "\"");
    }

    @Test
    void itemsInADropshipDeliveryGreyOutWarehouseMovesAndAddingItemsWithTheReason() {
        // given (was DropshipTemplateTest#orderDetailsGreysOutWarehouseMoves…, #…DisablesAddingItems…)
        Order order = order(OrderStatus.Assembly);
        OrderItem dropship = inDelivery(order, "delivery-9", FulfilmentStatus.Ordered);

        // when
        String html = page(render(order, List.of(dropship), ADMIN, Set.of(dropship.getItemId())));

        // then
        // a "disabled" produced by th:disabled has no literal counterpart in the source tag, so Thymeleaf
        // appends it at the end of the rendered tag's attributes, after everything th:attr expands
        assertThat(html).containsPattern("data-cl-bulk-action=\"[^\"]*moveSelectedItemsToTheWarehouse\"[^>]*disabled=\"disabled\"")
                .containsPattern("data-cl-bulk-action=\"[^\"]*moveSelectedItemsToAllocation\"[^>]*disabled=\"disabled\"")
                // the dropship lock disables every bulk action, not only the two above
                .containsPattern("data-cl-bulk-action=\"[^\"]*moveSelectedItemsToTheWarehouseForRMA\"[^>]*disabled=\"disabled\"")
                .containsPattern("data-cl-bulk-action=\"[^\"]*removeSelectedItemsFromOrder\"[^>]*disabled=\"disabled\"")
                .containsPattern("data-cl-dialog-open=\"item-add-dialog\"[^>]*disabled=\"disabled\"")
                .contains("id=\"add-items-reason\"");
        String reason = ResourceBundle.getBundle("messages", PL).getString("order.items.action.dropship.locked");
        assertThat(html).contains(reason);
    }

    @Test
    void anOrderWithNothingToDoOnItemsOffersNoBulkFallback() {
        // given (was DropshipTemplateTest#orderDetailsDisablesTheActionControlWhenNoItemActionIsAvailable)
        Order order = order(OrderStatus.Assembly);
        OrderItem dropship = inDelivery(order, "delivery-9", FulfilmentStatus.Ordered);
        order.setStatus(OrderStatus.Shipping);

        // when
        String locked = page(render(order, List.of(dropship), ADMIN, Set.of(dropship.getItemId())));

        // then: every bulk action is unavailable, so the no-script select-and-run block is not rendered at all
        assertThat(locked).doesNotContain("<noscript>");

        // positive control: the same item, on a New order and not in a dropship delivery, still gets the
        // fallback -- doesNotContain above would stay green even if <noscript> were removed outright
        String available = page(render(order(OrderStatus.New), List.of(dropship), ADMIN, Set.of()));
        assertThat(available).contains("<noscript>");
    }

    @Test
    void theNoScriptMoveFieldComesBeforeTheOtherBulkButtons() {
        // when
        String html = page(render(order(OrderStatus.New), ADMIN));
        String fallback = html.substring(html.indexOf("<noscript>"), html.indexOf("</noscript>"));

        // then: Enter in the order number field submits the first submit button of the form, which must be "move"
        int field = fallback.indexOf("id=\"move-target-fallback\"");
        int move = fallback.indexOf("/moveItemsToOrder\"");
        int confirm = fallback.indexOf("/bulk-confirm");
        assertThat(field).isNotNegative().isLessThan(move);
        assertThat(move).isLessThan(confirm);
        assertThat(fallback).contains("class=\"cl-selection-actions is-static\"")
                .contains("<label class=\"cl-label\" for=\"move-target-fallback\">")
                .contains("class=\"cl-input is-reference\"");
    }

    @Test
    void noScriptBulkButtonsLeadToTheConfirmationPage() {
        // when
        String html = page(render(order(OrderStatus.New), ADMIN));
        String fallback = html.substring(html.indexOf("<noscript>"), html.indexOf("</noscript>"));

        // then: nothing but "move" posts straight to the action, so removing items is never one click away
        assertThat(fallback).contains("formaction=\"/dashboard/orders/3e373abc-1111-2222-3333-444455556666/bulk-confirm?action=ALLOCATE\"")
                .contains("formaction=\"/dashboard/orders/3e373abc-1111-2222-3333-444455556666/bulk-confirm?action=REMOVE\"")
                .doesNotContain("/moveSelectedItemsToAllocation\"").doesNotContain("/removeSelectedItemsFromOrder\"")
                .containsPattern("class=\"cl-button is-danger\"\\s+formaction=\"[^\"]+action=REMOVE\"");
    }

    @Test
    void theBulkConfirmationPageListsTheItemsAndPostsThemToTheAction() {
        // given
        OrderItem cpu = items(order(OrderStatus.New)).get(0);
        cpu.setItemId("item-7");
        java.util.Map<String, Object> variables = new java.util.HashMap<>();
        variables.put("title", "Usunąć zaznaczone pozycje?");
        variables.put("message", "Usuniesz zaznaczone pozycje (1).");
        variables.put("confirmLabel", "Usuń pozycje");
        variables.put("danger", true);
        variables.put("actionPath", "/dashboard/orders/3e373abc/removeSelectedItemsFromOrder");
        variables.put("items", List.of(cpu));
        variables.put("orderId", "3e373abc");
        variables.put("backLabel", "Zamówienie 3e373abc");

        // when
        String html = page(SettingsTemplateRenderer.render("orders/bulk-confirm", variables));

        // then
        assertThat(html).contains("<h1 class=\"cl-page-title\">Usunąć zaznaczone pozycje?</h1>")
                .contains("Usuniesz zaznaczone pozycje (1).").contains("AMD Ryzen 7 9800X3D")
                .contains("action=\"/dashboard/orders/3e373abc/removeSelectedItemsFromOrder\"")
                .contains("name=\"orderItems[0].itemId\" value=\"item-7\"")
                .contains("name=\"orderItems[0].selected\" value=\"true\"")
                .contains("class=\"cl-button is-danger\"").contains("href=\"/dashboard/orders/3e373abc\"")
                .doesNotContain("style=");
    }

    @Test
    void theRowMenuTriggerIsATextGlyphThatShowsWithoutTheIconFont() {
        // when
        String html = page(render(order(OrderStatus.New), ADMIN));

        // then
        assertThat(html).contains("<span class=\"cl-menu-glyph\" aria-hidden=\"true\">⋯</span>")
                .doesNotContain("fa-ellipsis-h");
    }

    @Test
    void theGoodsIssueIsOfferedOnlyWhenTheStoreKeepsWarehouseDocumentsForWarehouseItems() {
        // given (was DropshipTemplateTest#orderDetailsHidesTheGoodsIssueActionForOrdersWithoutWarehouseItems)
        // the old test was vacuous — the test store never had warehouse documents enabled, so the
        // assertion passed whatever the flag. Make it two-sided: a warehouse item with the flag on shows the
        // action, the same order with the flag off does not.
        Order order = order(OrderStatus.Realization);
        List<OrderItem> items = items(order);

        // when
        String withDocumentsEnabled = page(render(order, items, ADMIN, Set.of(), true));
        String withDocumentsDisabled = page(render(order, items, ADMIN, Set.of(), false));

        // then
        assertThat(withDocumentsEnabled).contains("/goods-out");
        assertThat(withDocumentsDisabled).doesNotContain("/goods-out");
    }

    @Test
    void theOrderPageHasNoLegacyDropshipProviderChoice() {
        // when (was DropshipTemplateTest#orderDetailsNoLongerCarriesTheDropshipAction)
        String html = page(render(order(OrderStatus.Assembly), ADMIN));

        // then
        assertThat(html).doesNotContain("dropshipProvider").doesNotContain("order.action.dropship");
    }

    @Test
    void trackedShipmentsShowTheirTrackingStateAndUntrackedOnesDoNot() {
        // given (was ShipmentTrackingTemplateTest#orderShipmentsTableShowsTrackingSubscriptionStatus, #trackingColumnIsHidden…)
        Order tracked = order(OrderStatus.Shipping);
        Shipment parcel = tracked.getShipments().get(0);
        parcel.setCarrier("DPD");
        parcel.setTrackingNo("T-1");
        parcel.setShippedAt(LocalDateTime.of(2026, 9, 22, 10, 0));
        parcel.setTrackingSubscriptionStatus(ShipmentTrackingStatus.ACTIVE);
        Order untracked = order(OrderStatus.Shipping);

        // when
        String withTracking = page(render(tracked, ADMIN));
        String withoutTracking = page(render(untracked, ADMIN));

        // then
        assertThat(withTracking).contains("Śledzona w Furgonetce");
        assertThat(withoutTracking).doesNotContain("Śledzona w Furgonetce");
    }

    @Test
    void aTrackingLinkWithAScriptSchemeIsPrintedAsTextNotALink() {
        // given
        Order order = order(OrderStatus.Shipping);
        Shipment parcel = order.getShipments().get(0);
        parcel.setCarrier("DPD");
        parcel.setTrackingNo("T-1");
        parcel.setTrackingUrl("javascript:alert(1)");

        // when
        String html = page(render(order, ADMIN));

        // then
        assertThat(html).doesNotContain("href=\"javascript:");
        assertThat(html).containsPattern("<span>T-1</span>");
    }

    @Test
    void bothMoveTargetFieldsAcceptNoLongerANumberThanTheResolverLooksUp() {
        // when
        String html = page(render(order(OrderStatus.New), ADMIN));

        // then
        assertThat(html).containsPattern("<input[^>]*id=\"move-target\"[^>]*maxlength=\"64\"[^>]*>");
        assertThat(html).containsPattern("<input[^>]*id=\"move-target-fallback\"[^>]*maxlength=\"64\"[^>]*>");
    }

    @Test
    void theEditableDetailsPageLoadsMoneyJsExactlyOnce() {
        // given: fragments/item-add-modal.html and fragments/add-payment-modal.html are both included on this
        // page, and each used to load its own copy of money.js
        String html = render(order(OrderStatus.New), ADMIN);

        // when
        int count = occurrences(html, "/js/money.js");

        // then
        assertThat(count).isEqualTo(1);
    }

    @Test
    void theSupplierDialogChoosesAConnectionOrTypesAnotherSupplier() {
        // when (was SupplierLabelTemplatesTest: the order's quick-assign used supplier-choice :: field)
        String html = page(render(order(OrderStatus.New), ADMIN));

        // then
        assertThat(html).contains("id=\"assign-supplier-dialog\"").contains("name=\"supplier\"")
                .contains("value=\"" + SupplierChoice.CUSTOM + "\"").contains("name=\"customSupplier\"")
                .contains("Wpisz skrót kontrahenta");
    }

    static Map<String, Object> subpageVariables(Order order) {
        OrderPageModel page = factory(Set.of()).build(order, items(order), ADMIN, PL);
        Map<String, Object> variables = new HashMap<>();
        variables.put("navigation", null);
        variables.put("settings", page.settings());
        variables.put("statusOptions", page.statusOptions());
        variables.put("orderId", order.getOrderId());
        variables.put("shortId", order.getShortenedOrderId());
        return variables;
    }

    @Test
    void theSettingsPageWithoutJavascriptPostsTheSameFormAndCancelsBackToTheOrder() {
        // when
        String html = SettingsTemplateRenderer.render("orders/settings", subpageVariables(order(OrderStatus.Assembly)));

        // then
        assertThat(html).contains("id=\"order-settings-form\"")
                .contains("action=\"/dashboard/orders/3e373abc-1111-2222-3333-444455556666/updateOrderInfo\"")
                .contains("href=\"/dashboard/orders/3e373abc-1111-2222-3333-444455556666\"")
                .contains("Terminy i ustawienia zamówienia 3e373abc")
                .doesNotContain("data-cl-dialog-close-on-success").doesNotContain("data-cl-dialog-close")
                .doesNotContain("async-form.js").doesNotContain("??");
    }

    @Test
    void theAsyncSettingsAnswerRendersFromSettingsAloneWithItsError() {
        // given: on the async path the model attribute "order" is the posted form, so the fragment reads only settings
        Map<String, Object> variables = subpageVariables(order(OrderStatus.Assembly));
        variables.put("order", new Object());
        variables.put("settingsError", "Nie zapisano.");

        // when
        String html = SettingsTemplateRenderer.render("<div th:replace=\"~{orders/details/settings :: dialogForm}\"></div>", variables);

        // then
        assertThat(html).startsWith("<form").contains("data-cl-error-summary").contains("Nie zapisano.")
                .contains("data-cl-dialog-close-on-success=\"true\"").contains("id=\"settings-dialog-title\"");
    }

    @Test
    void theStatusPageWithoutJavascriptListsTheStatuses() {
        // when
        String html = SettingsTemplateRenderer.render("orders/status", subpageVariables(order(OrderStatus.Assembly)));

        // then
        assertThat(html).contains("action=\"/dashboard/orders/3e373abc-1111-2222-3333-444455556666/status\"")
                .containsPattern("name=\"status\" value=\"Assembly\" checked").doesNotContain("??");
    }

    static OrderSettingsView lockedSettings(FulfilmentType type) {
        return new OrderSettingsView("2026-09-20", "2026-09-22", null, "20.09.2026", "22.09.2026", null,
                type, type == null ? null : OrderLabels.fulfilmentType(type), true, false, null, null, null, true,
                OrderLabels.Option.of(FulfilmentType.values(), OrderLabels::fulfilmentType));
    }

    static String renderDialogForm(OrderSettingsView settings) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("settings", settings);
        variables.put("orderId", "3e373abc-1111-2222-3333-444455556666");
        variables.put("shortId", "3e373abc");
        return SettingsTemplateRenderer.render("<div th:replace=\"~{orders/details/settings :: dialogForm}\"></div>", variables);
    }

    @Test
    void aLockedFulfilmentTypeWithoutAValueRendersWithoutTheHiddenField() {
        // when
        String html = renderDialogForm(lockedSettings(null));

        // then
        assertThat(html).contains("id=\"fulfilmentType\"").doesNotContainPattern("<input[^>]*type=\"hidden\"[^>]*name=\"fulfilmentType\"");
    }

    @Test
    void aLockedFulfilmentTypeIsPostedThroughTheHiddenField() {
        // when
        String html = renderDialogForm(lockedSettings(FulfilmentType.WarehouseFulfilment));

        // then
        assertThat(html).containsPattern("<input[^>]*type=\"hidden\"[^>]*name=\"fulfilmentType\"[^>]*value=\"WarehouseFulfilment\"");
    }

    static String card(String html, String id) {
        int start = html.indexOf("id=\"" + id + "\"");
        return html.substring(start, html.indexOf("</section>", start));
    }

    static String dialog(String html, String id) {
        int start = html.indexOf("id=\"" + id + "\"");
        return html.substring(start, html.indexOf("</dialog>", start));
    }

    static Payment refund(double amount) {
        return new Payment("ZW/1", "Zwrot", PaymentSource.BankTransfer, pl.commercelink.orders.PaymentDirection.Outgoing,
                amount, 0, null, null);
    }

    @Test
    void aRefundShowsOneMinus() {
        // given: the dialog asks for a minus, supplier payouts are stored positive
        Order typedNegative = order(OrderStatus.Realization);
        typedNegative.addPayment(refund(-100));
        Order storedPositive = order(OrderStatus.Realization);
        storedPositive.addPayment(refund(100));

        // when
        String negative = card(page(render(typedNegative, ADMIN)), "platnosci");
        String positive = card(page(render(storedPositive, ADMIN)), "platnosci");

        // then
        for (String html : List.of(negative, positive)) {
            String list = html.substring(html.indexOf("<ul class=\"cl-list\""), html.indexOf("</ul>"));
            assertThat(list).doesNotContain("−−").contains("−100,00 PLN").contains(">Zwrot<");
            assertThat(occurrences(list, "−")).as(list).isEqualTo(1);
        }
    }

    @Test
    void anExpectedPaymentReadsAsExpectedInsteadOfAZeroAmount() {
        // given
        Order order = order(OrderStatus.New);
        order.addPayment(new Payment(PaymentSource.BankTransfer));

        // when
        String html = card(page(render(order, ADMIN)), "platnosci");
        String list = html.substring(html.indexOf("<ul class=\"cl-list\""), html.indexOf("</ul>"));

        // then
        assertThat(list).contains("Oczekiwana wpłata").contains("cl-status is-neutral").contains("nierozliczona")
                .doesNotContain("0,00");
    }

    @Test
    void anOverpaidOrderShowsTheOverpaymentNotANegativeDue() {
        // given
        Order order = order(OrderStatus.Realization);
        order.addPayment(Payment.bankTransfer("R/1", "Jan", 2 * 749 + 299 + 1));

        // when
        String html = card(page(render(order, ADMIN)), "platnosci");

        // then
        assertThat(html).contains("Nadpłata: 1,00 PLN").contains("is-warn").doesNotContain("−1,00")
                .doesNotContain("Do zapłaty");
    }

    @Test
    void aPaymentFeeIsFormattedWithTheSharedCurrencyKey() {
        // given
        Order order = order(OrderStatus.Realization);
        order.addPayment(new Payment("R/1", "Jan", PaymentSource.BankTransfer, 100, 2.5));

        // when
        String html = card(page(render(order, ADMIN)), "platnosci");

        // then
        assertThat(html).contains("prowizja 2,50 PLN").doesNotContain("PLN PLN");
    }

    @Test
    void aFailedTrackingSubscriptionIsABadPillWithItsExplanation() {
        // given
        Order order = order(OrderStatus.Shipping);
        Shipment parcel = order.getShipments().get(0);
        parcel.setCarrier("DPD");
        parcel.setTrackingNo("T-1");
        parcel.setTrackingSubscriptionStatus(ShipmentTrackingStatus.FAILED);
        String help = ResourceBundle.getBundle("messages", PL).getString("order.shipment.tracking.failed.help");

        // when
        String html = card(page(render(order, ADMIN)), "przesylki");

        // then
        assertThat(html).containsPattern("<span class=\"cl-status is-bad\" title=\"" + Pattern.quote(help) + "\">Błąd subskrypcji</span>");
    }

    @Test
    void theReviewDialogOfAnOrderWithoutAReviewPreselectsNotCollected() {
        // given
        Order withoutReview = order(OrderStatus.Delivered);
        withoutReview.setReview(null);

        // when
        String none = dialog(page(render(withoutReview, ADMIN)), "review-dialog");
        String collected = dialog(page(render(order(OrderStatus.Delivered), ADMIN)), "review-dialog");

        // then
        assertThat(none).contains("<option value=\"\" selected>— nie zbieramy —</option>").contains("Status opinii");
        assertThat(collected).doesNotContain("nie zbieramy")
                .containsPattern("<option value=\"ToBeCollected\"\\s+selected=\"selected\">Do zebrania</option>");
    }

    @Test
    void theConsolidatedPillIsInformationNotAState() {
        // given
        Order order = order(OrderStatus.New);
        List<OrderItem> items = items(order);
        items.get(0).setConsolidated(true);

        // when
        String html = page(render(order, items, ADMIN, Set.of()));

        // then
        assertThat(html).contains("<span class=\"cl-status is-info\">Na fakturze łącznie</span>");
    }

    @Test
    void theSkuAndSerialPrefixesComeFromTheBundles() throws Exception {
        // given
        String template = java.nio.file.Files.readString(
                java.nio.file.Path.of("src/main/resources/templates/orders/details/items.html"));

        // when
        String html = page(render(order(OrderStatus.New), ADMIN));

        // then
        assertThat(template).doesNotContain("· SKU").doesNotContain("· SN")
                .contains("#{order.items.sku.prefix}").contains("#{order.items.sn.prefix}");
        assertThat(html).contains(">Stan</th>").doesNotContain(">Realizacja</th>");
    }

    @Test
    void theReasonABulkActionIsUnavailableIsVisibleTextTheButtonPointsTo() {
        // given
        Order order = order(OrderStatus.Assembly);
        OrderItem dropship = inDelivery(order, "delivery-9", FulfilmentStatus.Ordered);
        String reason = ResourceBundle.getBundle("messages", PL).getString("order.items.action.dropship.locked");

        // when
        String html = page(render(order, List.of(dropship), ADMIN, Set.of(dropship.getItemId())));
        String bar = html.substring(html.indexOf("data-cl-selection-bar"), html.indexOf("data-cl-select-clear"));

        // then
        assertThat(bar).contains("<p class=\"cl-help is-note\" id=\"bulk-reason-0\">" + reason + "</p>")
                .containsPattern("data-cl-bulk-action=\"[^\"]*moveSelectedItemsToAllocation\"[^>]*aria-describedby=\"bulk-reason-0\"")
                .doesNotContainPattern("\\stitle=").doesNotContain("cl-visually-hidden");
        assertThat(occurrences(bar, reason)).isEqualTo(1);
    }

    @Test
    void theSelectionActionsCarryTheScopeCountTemplate() {
        // when
        String html = page(render(order(OrderStatus.New), ADMIN));

        // then
        assertThat(html).contains("data-cl-scope-count-template=\"{k} z {n}\"");
    }

    @Test
    void thePreferredShippingExplanationIsVisibleNotATooltip() {
        // when
        String html = page(render(order(OrderStatus.New), ADMIN));

        // then
        assertThat(html).doesNotContainPattern("<dt[^>]*title=")
                .contains("<p class=\"cl-help is-note\">Ustawia klient na stronie zamówienia.</p>")
                .containsPattern("<div class=\"cl-kv-wide\"><dt>Preferowana");
    }

    @Test
    void theEmptyDocumentsTextLeadsAConsumerOrderToAddDocument() {
        // when
        String b2c = card(page(render(order(OrderStatus.Realization), ADMIN)), "dokumenty");
        String b2b = card(page(render(b2bOrder(OrderStatus.Realization), ADMIN)), "dokumenty");

        // then
        assertThat(b2c).contains("Brak dokumentów. Paragon dodasz przyciskiem „Dodaj dokument”.");
        assertThat(b2b).contains("Brak dokumentów. Następny do wystawienia: Faktura VAT.");
    }

    @Test
    void theIssueDialogTitleIsOneSentenceFilledWithTheType() {
        // when
        String html = dialog(page(render(b2bOrder(OrderStatus.Realization), ADMIN)), "issue-dialog");

        // then
        assertThat(html).contains("data-template=\"Wystawić dokument: {type}?\"").contains("data-cl-issue-title");
    }

    @Test
    void theShipmentsEmptyTextSaysWhatCanBeDone() {
        // given
        Order order = order(OrderStatus.New);
        order.setShipments(new java.util.ArrayList<>());

        // when
        String html = card(page(render(order, ADMIN)), "przesylki");

        // then
        assertThat(html).contains("Brak przesyłek. Dodaj przesyłkę przyciskiem „Edytuj przesyłki”.");
    }
}
