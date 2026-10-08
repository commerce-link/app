package pl.commercelink.web;

import org.springframework.context.support.ResourceBundleMessageSource;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import pl.commercelink.documents.Document;
import pl.commercelink.documents.DocumentType;
import pl.commercelink.inventory.deliveries.DeliveryRedirectResolver;
import pl.commercelink.inventory.deliveries.DropshipItemLookup;
import pl.commercelink.inventory.supplier.SupplierChoice;
import pl.commercelink.inventory.supplier.SupplierLabelMap;
import pl.commercelink.inventory.supplier.SupplierLabels;
import pl.commercelink.orders.BillingDetails;
import pl.commercelink.orders.CourierCancellation;
import pl.commercelink.orders.FulfilmentStatus;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.orders.OrderReview;
import pl.commercelink.orders.OrderReviewStatus;
import pl.commercelink.orders.OrderSource;
import pl.commercelink.orders.OrderSourceType;
import pl.commercelink.orders.OrderStatus;
import pl.commercelink.orders.Payment;
import pl.commercelink.orders.PaymentSource;
import pl.commercelink.orders.PositionGroup;
import pl.commercelink.orders.Shipment;
import pl.commercelink.orders.ShipmentCarrierOptions;
import pl.commercelink.orders.ShipmentCreationState;
import pl.commercelink.orders.ShipmentPickup;
import pl.commercelink.orders.ShipmentTrackingStatus;
import pl.commercelink.orders.ShipmentType;
import pl.commercelink.orders.ShippingDetails;
import pl.commercelink.orders.event.EventType;
import pl.commercelink.orders.event.OrderEvent;
import pl.commercelink.orders.event.OrderEventsRepository;
import pl.commercelink.orders.fulfilment.FulfilmentType;
import pl.commercelink.products.ProductCatalogRepository;
import pl.commercelink.receipts.ReceiptAlerts;
import pl.commercelink.receipts.ReceiptAttempt;
import pl.commercelink.receipts.ReceiptPageProblem;
import pl.commercelink.receipts.ReceiptAttemptService;
import pl.commercelink.receipts.ReceiptOrderState;
import pl.commercelink.receipts.ReceiptOrderView;
import pl.commercelink.receipts.ReceiptAttemptState;
import pl.commercelink.shipping.ShippingService;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.taxonomy.TaxonomyCache;
import pl.commercelink.warehouse.api.ItemCondition;
import pl.commercelink.web.orders.OrderPageModel;
import pl.commercelink.web.orders.OrderPageModelFactory;
import pl.commercelink.web.orders.OrderAddressForm;
import pl.commercelink.web.orders.OrderLabels;
import pl.commercelink.web.orders.OrderSettingsView;
import pl.commercelink.web.orders.OrderPaymentForm;
import pl.commercelink.web.orders.OrderShipmentForm;
import pl.commercelink.web.dtos.AssignSupplierForm;
import pl.commercelink.web.settings.SettingsTemplateRenderer;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.regex.Matcher;
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
        return factory(dropshipItemIds, documentsGenerationEnabled, List.of());
    }

    static OrderPageModelFactory factory(Set<String> dropshipItemIds, boolean documentsGenerationEnabled,
                                         List<OrderEvent> orderEvents) {
        return factory(dropshipItemIds, documentsGenerationEnabled, orderEvents, ReceiptOrderState.NONE);
    }

    /** receipts: what the order's e-receipt attempts say (ReceiptAttemptService#orderState is stubbed with it). */
    static OrderPageModelFactory factory(Set<String> dropshipItemIds, boolean documentsGenerationEnabled,
                                         List<OrderEvent> orderEvents, ReceiptOrderState receipts) {
        StoresRepository stores = mock(StoresRepository.class);
        Store store = new Store();
        store.setStoreId("store-1");
        store.setName("Demo");
        // names the integration of a shipment typed in by hand (ShippingIntegrationNames)
        store.setConfigurationValue(pl.commercelink.stores.IntegrationType.SHIPPING_PROVIDER, "furgonetka");
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
        when(events.findByOrderId(anyString())).thenReturn(orderEvents);
        ShipmentCarrierOptions carrierOptions = mock(ShipmentCarrierOptions.class);
        when(carrierOptions.forOrder(any(), any())).thenReturn(List.of("DPD", "InPost"));
        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding("UTF-8");
        messages.setFallbackToSystemLocale(false);
        ReceiptAttemptService receiptService = mock(ReceiptAttemptService.class);
        when(receiptService.orderState(any(), any(), any(), any())).thenReturn(receipts);
        OrderPageModelFactory factory = new OrderPageModelFactory(stores, events, dropship, new DeliveryRedirectResolver(),
                pl.commercelink.web.orders.DropshipEligibilityStubs.acceptingEverySupplier(), labels, carrierOptions, mock(ProductCatalogRepository.class), mock(TaxonomyCache.class), messages,
                receiptService, mock(ReceiptAlerts.class), courierAvailable(),
                pl.commercelink.shipping.ShippingIntegrationNamesFixture.names());
        ReflectionTestUtils.setField(factory, "appDomain", "https://app.example");
        return factory;
    }

    /** A store with a courier account: the page offers "Nadaj przesyłkę" where a shipment waits for it. */
    static ShippingService courierAvailable() {
        ShippingService shipping = mock(ShippingService.class);
        when(shipping.isAvailableFor(any(), any())).thenReturn(true);
        when(shipping.supportsLabels(any(), any())).thenReturn(true);
        return shipping;
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
        return renderPage(page, order);
    }

    static String renderPage(OrderPageModel page, Order order) {
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
        int items = html.indexOf("id=\"pozycje\"");
        int shipments = html.indexOf("id=\"przesylki\"");
        assertThat(items).isPositive().isLessThan(shipments);
        assertThat(html).doesNotContain("data-order=").contains("data-cl-collapse");
    }

    @Test
    void theFulfilmentTypeShowsAsAnIconAndAShortLabelInTheHeaderOnly() {
        // given: WarehouseFulfilment is order()'s default
        Order warehouse = order(OrderStatus.Assembly);
        Order dropship = order(OrderStatus.Assembly);
        dropship.setFulfilmentType(FulfilmentType.DirectToConsumer);

        // when
        String warehouseFullHtml = page(render(warehouse, ADMIN));
        String dropshipFullHtml = page(render(dropship, ADMIN));
        String warehouseHeader = header(warehouseFullHtml);
        String warehouseSettings = card(warehouseFullHtml, "settings-title");
        String dropshipHeader = header(dropshipFullHtml);
        String dropshipSettings = card(dropshipFullHtml, "settings-title");

        // then: decorative icon + short text in the header, not the long store-settings label; the settings card no
        // longer repeats it
        assertThat(warehouseHeader).contains("cl-icon-text").contains("fas fa-warehouse\" aria-hidden=\"true\"")
                .contains("Magazyn sklepu").doesNotContain("Przez magazyn sklepu");
        assertThat(dropshipHeader).contains("cl-icon-text").contains("fas fa-truck\" aria-hidden=\"true\"")
                .contains("Dropshipping").doesNotContain("Wysyłką od dostawcy do klienta");
        for (String html : List.of(warehouseSettings, dropshipSettings)) {
            assertThat(html).doesNotContain("Typ realizacji").doesNotContain("cl-icon-text")
                    .doesNotContain("Magazyn sklepu").doesNotContain("Dropshipping");
        }
        // then: the settings dialog/no-JS form still offers the full labels to choose from
        assertThat(warehouseFullHtml).contains("Przez magazyn sklepu").contains("Wysyłką od dostawcy do klienta");
    }

    /** The record header: everything before the main/side card layout starts. */
    static String header(String html) {
        return html.substring(0, html.indexOf("cl-layout-aside"));
    }

    @Test
    void theCardsFollowOneReadingOrderInTheHtmlSoPhonesNeedNoReordering() {
        // when
        String html = page(render(order(OrderStatus.Assembly), ADMIN));

        // then: main column first, side column after (dates and settings, finances, customer, history — the client's
        // order, customer moved below finances on 2026-09-29); the stylesheet stacks them in this same order below 1366 px and never reorders them
        List<Integer> positions = List.of(html.indexOf("id=\"items-title\""), html.indexOf("id=\"shipments-title\""),
                html.indexOf("id=\"documents-title\""), html.indexOf("id=\"payments-title\""),
                html.indexOf("id=\"settings-title\""), html.indexOf("id=\"finances-title\""),
                html.indexOf("id=\"customer-title\""), html.indexOf("id=\"history-title\""));
        assertThat(positions).doesNotContain(-1).isSorted();
    }

    @Test
    void theItemsTableHasSixColumnsAndServicesInTheirOwnGroup() {
        // when
        String html = page(render(order(OrderStatus.New), ADMIN));

        // then
        assertThat(html).contains("<col class=\"cl-col-check\">").contains("<col class=\"cl-col-flag\">").contains("<col class=\"cl-col-menu\">")
                .contains("class=\"cl-table-group\"").contains("Usługi i dostawa")
                .contains("2 × 749,00").doesNotContain("koszt 712,17 brutto")
                .contains("data-cl-copy=\"100-100001084WOF\"")
                .containsPattern("data-ready-for-allocation=\"false\"[^>]*data-removable=\"true\"")
                .contains("name=\"orderItems[0].selected\"").contains("name=\"orderItems[0].itemId\"");
    }

    @Test
    void menuEntriesThatCannotBeDoneAreAbsentAndTheRestKeepTheirReason() {
        // given
        Order order = order(OrderStatus.New);
        OrderItem allocated = inDelivery(order, "Acme", FulfilmentStatus.Allocation);

        // when
        String html = page(render(order, ADMIN));
        String withSupplier = page(render(order, List.of(allocated), ADMIN, Set.of()));

        // then: "Split set" is absent for an item that is not a set, and so is the side that cannot be done: "Usuń
        // dostawcę" without a supplier, the assign entries while a supplier is still held
        assertThat(html).doesNotContain("<span>Usuń dostawcę</span>").doesNotContain("Pozycja nie ma dostawcy")
                .doesNotContain("<span>Podziel zestaw</span>").doesNotContain("data-cl-dialog-open=\"split-group-dialog\"")
                .contains("data-cl-dialog-open=\"assign-sku-dialog\"").contains("data-cl-dialog-open=\"assign-supplier-dialog\"");
        assertThat(withSupplier).contains("/clear-supplier?itemId=").contains("data-cl-confirm")
                .doesNotContain("Najpierw usuń obecnego dostawcę").doesNotContain("data-cl-dialog-open=\"assign-sku-dialog\"")
                .doesNotContain("data-cl-dialog-open=\"assign-supplier-dialog\"");
    }

    @Test
    void theItemMenuSendsOneItemToAllocationThroughItsOwnFormOrSaysWhyNot() {
        // given
        Order order = order(OrderStatus.New);
        OrderItem ready = items(order).get(0);
        ready.setItemId("item-ready");
        ready.setEan("5900000000001");
        ready.setDeliveryId("Acme");

        // when
        String withoutSupplier = page(render(order, ADMIN));
        String html = page(render(order, List.of(ready), ADMIN, Set.of()));

        // then: the entry posts the item as the one selected item of the bulk endpoint, without JavaScript too
        assertThat(html).containsPattern("<form id=\"allocate-item-form\" method=\"post\"\\s+action=\"/dashboard/orders/"
                        + "3e373abc-1111-2222-3333-444455556666/moveSelectedItemsToAllocation\">\\s*"
                        + "(<input type=\"hidden\" name=\"_csrf\"[^>]*>\\s*)?"
                        + "<input type=\"hidden\" name=\"orderItems\\[0\\].selected\" value=\"true\">")
                .containsPattern("<button type=\"submit\" class=\"cl-menu-item\" form=\"allocate-item-form\" "
                        + "name=\"orderItems\\[0\\].itemId\"\\s+value=\"item-ready\">Do alokacji</button>");
        assertThat(withoutSupplier).containsPattern("aria-disabled=\"true\">\\s*<span>Do alokacji</span>\\s*"
                + "<span class=\"cl-menu-reason\">Najpierw przypisz dostawcę</span>");
    }

    @Test
    void anItemInHandReadsAssembledWhileTheWarehouseDialogKeepsItsOwnWord() {
        // given
        Order order = order(OrderStatus.Assembly);

        // when
        String html = page(render(order, ADMIN));

        // then: the item state pill of the delivered service says "Skompletowany"; the order status keeps "Dostarczone"
        // and the warehouse stock in the "Przypisz z magazynu" dialog is still "Dostarczony" (in stock)
        assertThat(html).containsPattern("<span class=\"cl-status[^\"]*\">Skompletowany</span>")
                .doesNotContainPattern("<span class=\"cl-status[^\"]*\">Dostarczony</span>")
                .contains("data-status-delivered=\"Dostarczony\"");
        assertThat(ResourceBundle.getBundle("messages", Locale.ENGLISH).getString("order.item.status.Delivered"))
                .isEqualTo("Assembled");
        // the warehouse screens localise the stock state through the enum's own key (EnumLocalizer): unchanged
        assertThat(ResourceBundle.getBundle("messages", PL).getString("FulfilmentStatus.Delivered")).isEqualTo("Dostarczony");
        assertThat(ResourceBundle.getBundle("messages", Locale.ENGLISH).getString("FulfilmentStatus.Delivered")).isEqualTo("Delivered");
        assertThat(ResourceBundle.getBundle("messages", PL).getString("OrderStatus.Delivered")).isEqualTo("Dostarczone");
    }

    @Test
    void theItemStatePillIsTheLinkToItsDelivery() {
        // given
        Order order = order(OrderStatus.Assembly);
        OrderItem ordered = inDelivery(order, "90fd6ba3-1111-2222-3333-444455556666", FulfilmentStatus.Ordered);

        // when
        String cell = fulfilmentCell(page(render(order, List.of(ordered), ADMIN, Set.of())));

        // then: the pill took the link of the delivery number that stood under it (client 2026-10-06); the number is
        // gone from sight and named for screen readers only
        assertThat(cell).containsPattern("^<a class=\"cl-status is-info\" "
                        + "href=\"/dashboard/deliveries/details\\?deliveryId=90fd6ba3-1111-2222-3333-444455556666\" "
                        + "aria-label=\"Zamówiony, dostawa 90fd6ba3\">Zamówiony</a>$")
                .doesNotContain("cl-table-sub").doesNotContain("cl-table-link");
    }

    @Test
    void anItemWaitingForItsSupplierLinksCreatingTheDeliveryForAnAdminOnly() {
        // given: a warehouse order's item waiting for AcmeB, not yet in a delivery
        Order order = order(OrderStatus.Assembly);
        OrderItem awaiting = inDelivery(order, "AcmeB", FulfilmentStatus.Allocation);

        // when
        String admin = fulfilmentCell(page(render(order, List.of(awaiting), ADMIN, Set.of())));
        String user = fulfilmentCell(page(render(order, List.of(awaiting), USER, Set.of())));

        // then: creating a delivery is the admin's; a user sees the plain pill, never the supplier's name
        assertThat(admin).contains("href=\"/dashboard/deliveries/create/AcmeB\"")
                .contains("aria-label=\"W alokacji, dostawca: AcmeB\"");
        assertThat(user).isEqualTo("<span class=\"cl-status is-info\">W alokacji</span>");
    }

    @Test
    void theStoresWarehouseAndAnItemWithoutSupplierKeepThePlainPill() {
        // given
        Order order = order(OrderStatus.Assembly);
        OrderItem fromWarehouse = inDelivery(order, OrderItem.GENERIC_WAREHOUSE_ORDER_NO, FulfilmentStatus.Delivered);
        OrderItem unassigned = inDelivery(order, null, FulfilmentStatus.New);

        // when
        String warehouse = fulfilmentCell(page(render(order, List.of(fromWarehouse), ADMIN, Set.of())));
        String none = fulfilmentCell(page(render(order, List.of(unassigned), ADMIN, Set.of())));

        // then: the client wants the pill to lead to a delivery or its creation and nothing else
        assertThat(warehouse).isEqualTo("<span class=\"cl-status is-ok\">Skompletowany</span>");
        assertThat(none).isEqualTo("<span class=\"cl-status is-neutral\">Nowy</span>");
    }

    /** The inside of the first item's state cell, trimmed. */
    private static String fulfilmentCell(String html) {
        String open = "<td class=\"cl-table-fulfilment\" data-label=\"Stan\">";
        int start = html.indexOf(open) + open.length();
        return html.substring(start, html.indexOf("</td>", start)).strip().replaceAll("\\s+", " ");
    }

    @Test
    void theMoreMenuNoLongerNamesTheItemHistory() {
        // given
        Order order = order(OrderStatus.Delivered);
        List<OrderItem> items = items(order);
        items.get(0).setSerialNo("SN-1");

        // when
        String html = page(render(order, items, ADMIN, Set.of()));
        String more = html.substring(html.indexOf("id=\"order-more-menu\""));
        more = more.substring(0, more.indexOf("</ul>"));

        // then: the serial number in the item's row still leads to its history
        assertThat(more).doesNotContain("Historia przedmiotu").doesNotContain("/dashboard/item/history");
        assertThat(html).contains("href=\"/dashboard/item/history?serialNo=SN-1\"");
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
        Matcher menus = Pattern.compile("<(\\w+)[^>]*class=\"cl-menu\"").matcher(html);
        // the selection row's menus are left out of the fallback check: the row is hidden until table-select.js shows
        // it, and without JavaScript the <noscript> block under the table offers the same actions
        int rowStart = html.indexOf("data-cl-selection-bar");
        String withoutSelectionRow = html.substring(0, rowStart) + html.substring(html.indexOf("<table", rowStart));
        int count = 0;
        while (menus.find()) {
            assertThat(menus.group(1)).as(menus.group()).isEqualTo("details");
            count++;
        }
        assertThat(count).as("Więcej, Wystaw and a row menu per item").isGreaterThanOrEqualTo(4);
        assertThat(html).doesNotContain("aria-controls=\"order-more-menu\"").doesNotContainPattern("class=\"cl-menu-list\"[^>]*hidden")
                .containsPattern("<summary class=\"cl-button is-icon\" aria-label=\"[^\"]+\"");
        int openers = 0;
        for (String menu : withoutSelectionRow.split("<details class=\"cl-menu\"")) {
            String body = menu.contains("</details>") ? menu.substring(0, menu.indexOf("</details>")) : "";
            Matcher opener = Pattern.compile("<\\w+[^>]*data-cl-dialog-open=[^>]*>").matcher(body);
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
    void aUserSeesTheItemCostAndProfitLikeAnAdmin() {
        // when
        String user = page(render(order(OrderStatus.New), USER));
        String admin = page(render(order(OrderStatus.New), ADMIN));

        // then
        assertThat(user).contains("Koszt: 712,17 PLN brutto / szt.").contains("Zysk (z VAT)").contains("Koszt produktów (brutto)");
        assertThat(admin).contains("Koszt: 712,17 PLN brutto / szt.").contains("Zysk (z VAT)").contains("Koszt produktów (brutto)");
    }

    @Test
    void theCostLineUnderThePriceBecameAMarginIconWhoseTooltipCarriesTheNumbers() {
        // when
        String html = page(render(order(OrderStatus.New), USER));

        // then: 749,00 for 579 net + 23% (712,17 gross); the service has no cost, so its margin cannot be computed
        assertThat(html).doesNotContain("koszt 712,17 brutto")
                .containsPattern("<span class=\"cl-price-margin\"><span>2 × 749,00</span><span class=\"cl-mark cl-margin cl-tooltip is-lines is-end is-ok\" tabindex=\"0\" role=\"img\"")
                .contains("data-tooltip=\"Marża: 4,9%\nZysk: 29,94 PLN netto (36,83 PLN brutto) / szt.\nKoszt: 712,17 PLN brutto / szt.\"")
                .contains("class=\"fas fa-info-circle\"")
                .contains("cl-mark cl-margin cl-tooltip is-lines is-end is-unknown")
                .contains("Brak kosztu zakupu — marży nie da się policzyć.");
    }

    @Test
    void aSaleBelowCostIsMarkedByItsGlyphNotOnlyItsColour() {
        // given
        Order order = order(OrderStatus.New);
        OrderItem cheap = new OrderItem(order.getOrderId(), "CPU", "AMD Ryzen 5", 1, 500, "MFN-5", false, 0);
        cheap.setStatus(FulfilmentStatus.New);
        cheap.setCost(450);

        // when
        String html = page(render(order, List.of(cheap), USER, Set.of()));

        // then: 450 net + 23% = 553,50 gross against 500,00
        assertThat(html).contains("cl-mark cl-margin cl-tooltip is-lines is-end is-loss")
                .contains("Sprzedaż poniżej kosztu: marża −10,7%\nZysk: −43,50 PLN netto (−53,50 PLN brutto) / szt.")
                .contains("class=\"fas fa-exclamation-circle\"");
    }

    @Test
    void thePriceColumnNamesTheCurrencyOfThePageAmounts() {
        // when
        String html = page(render(order(OrderStatus.New), USER));

        // then: the header and the card-mode label carry the unit; the cells keep bare numbers
        assertThat(html).contains("<th scope=\"col\" class=\"is-numeric\">Ilość × cena (PLN)</th>")
                .contains("<td class=\"is-numeric\" data-label=\"Ilość × cena (PLN)\">")
                .contains("<span>2 × 749,00</span>");
    }

    @Test
    void theFinancesCardShowsTheTotalWhatWasPaidAndWhatIsDueAboveTheCosts() {
        // when
        String html = page(render(order(OrderStatus.New), USER));

        // then: the products and services values are gone at the client's request
        String card = card(html, "finances-title");
        String totals = card.substring(0, card.indexOf("id=\"finances-costs\""));
        assertThat(card).doesNotContain("Wartość produktów").doesNotContain("Wartość usług");
        assertThat(Pattern.compile("<dt>([^<]+)</dt>").matcher(totals).results().map(m -> m.group(1)).toList())
                .containsExactly("Razem", "Wpłacono", "Do zapłaty");
    }

    @Test
    void costAndProfitAreACollapsedSectionOfTheFinancesCardForEveryRole() {
        // when
        String user = page(render(order(OrderStatus.New), USER));

        // then
        Matcher costs = Pattern.compile("(?s)<details class=\"cl-disclosure\" id=\"finances-costs\">(.*?)</details>")
                .matcher(user);
        assertThat(costs.find()).isTrue();
        assertThat(costs.group(1)).contains("<summary>Koszt i zysk</summary>").contains("Zysk (bez VAT)");
        assertThat(user).doesNotContain("cl-kv-group");
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
        assertThat(html).contains("id=\"customer-title\"").contains(">Klient<").doesNotContain("??");
    }

    @Test
    void eachShipmentHasEditAndRemoveAndTheCardHeadAddsOne() {
        // given: the second shipment has a courier order, cancelled with "Cancel shipment" instead of removed
        Order order = order(OrderStatus.Realization);
        Shipment labelled = new Shipment(ShipmentType.Courier);
        labelled.setCarrier("DPD");
        labelled.setTrackingNo("T-1");
        labelled.setShippedAt(java.time.LocalDateTime.of(2026, 9, 28, 9, 0));
        labelled.setExternalId("EXT-1");
        order.addShipment(labelled);
        String version = OrderShipmentForm.version(order.getShipments().get(0));

        // when
        String card = card(page(render(order, ADMIN)), "przesylki");

        // then
        assertThat(card).contains("href=\"/dashboard/orders/" + order.getOrderId() + "/shipments/new\"")
                .contains("data-cl-dialog-open=\"shipment-dialog-new\"").contains(">Dodaj przesyłkę<")
                .contains("data-cl-dialog-open=\"shipment-dialog-0\"").contains("aria-label=\"Edytuj przesyłkę 1\"")
                .contains("data-cl-dialog-open=\"shipment-dialog-1\"").contains("aria-label=\"Edytuj przesyłkę 2\"")
                .contains("/shipments/0/remove?version=" + version).contains("aria-label=\"Usuń przesyłkę 1\"")
                .contains("data-cl-confirm-title=\"Usunąć przesyłkę 1?\"")
                .contains("data-cl-confirm-message=\"Przesyłka zniknie z zamówienia. Klient nie dostanie o tym wiadomości.\"")
                .doesNotContain("/shipments/1/remove").doesNotContain("Edytuj przesyłki")
                .contains("id=\"shipment-2-remove-reason\">Najpierw anuluj przesyłkę, potem ją usuniesz.</p>")
                .doesNotContain("shipment-1-remove-reason");
    }

    @Test
    void aCancellationInProgressShowsItsPillGreysTheCancelActionAndAsksThePageToPoll() {
        // given
        Order order = order(OrderStatus.Shipping);
        Shipment sent = order.getShipments().get(0);
        sent.setCarrier("DPD");
        sent.setTrackingNo("T-1");
        sent.setShippedAt(java.time.LocalDateTime.now().minusHours(1));
        sent.setExternalId("EXT-1");
        sent.setCancellation(CourierCancellation.pending("cmd-1", java.time.LocalDateTime.now()));

        // when
        String html = render(order, ADMIN);
        String card = card(page(html), "przesylki");

        // then
        assertThat(card).contains("data-cl-cancellation-poll=\"/dashboard/orders/" + order.getOrderId()
                        + "/shipments/cancellation-state\"")
                .containsPattern("<span class=\"cl-status is-info\">Anulowanie w toku</span>")
                .containsPattern("<button type=\"button\" class=\"cl-link-button\"\\s+aria-disabled=\"true\"[^>]*"
                        + "aria-describedby=\"shipment-cancel-reason\">Anuluj przesyłkę</button>")
                .contains("id=\"shipment-cancel-reason\">Anulowanie już trwa — czekamy na potwierdzenie z integracji "
                        + "wysyłki (Furgonetka).</p>")
                .doesNotContain("/cancelShipment\"");
        assertThat(html).contains("/js/shipment-cancellation.js");
    }

    private static Shipment integrationShipment(Order order) {
        Shipment shipment = order.getShipments().get(0);
        shipment.setProvider("furgonetka");
        shipment.setCarrier("DPD");
        shipment.setPickUpAddressId("addr-1");
        return shipment;
    }

    @Test
    void aShipmentBeingCreatedShowsTheSpinnerLineAndThePagePolls() {
        // given
        Order order = order(OrderStatus.Shipping);
        integrationShipment(order).setCreation(ShipmentCreationState.pending("cmd-1", java.time.LocalDateTime.now()));

        // when
        String card = card(page(render(order, ADMIN)), "przesylki");

        // then
        assertThat(card).contains("data-cl-cancellation-poll=\"/dashboard/orders/" + order.getOrderId()
                        + "/shipments/cancellation-state\"")
                .containsPattern("<p class=\"cl-list-desc cl-loading\"><span class=\"cl-spinner is-compact\" aria-hidden=\"true\"></span><span>Nadawanie…</span></p>")
                .doesNotContain(">Edytuj<")
                .contains("Przesyłka jest nadawana — poczekaj na wynik.");
    }

    @Test
    void aFailedCreationShowsTheProviderReasonWithRetryAndRemoveButNoEdit() {
        // given
        Order order = order(OrderStatus.Shipping);
        Shipment failed = integrationShipment(order);
        failed.setCreation(ShipmentCreationState.pending("cmd-1", java.time.LocalDateTime.now()).failed("Brak środków na koncie"));

        // when
        String card = card(page(render(order, ADMIN)), "przesylki");

        // then: the array of arguments is spread, never printed as one value
        assertThat(card).contains("<span class=\"cl-status is-warn\">Nie udało się nadać: Brak środków na koncie</span>")
                .doesNotContain("[Ljava")
                .contains("href=\"/dashboard/orders/" + order.getOrderId() + "/shipping?provider=furgonetka\"")
                .contains(">Spróbuj ponownie</a>")
                .contains("/shipments/0/remove")
                .doesNotContain(">Edytuj<")
                .doesNotContain("data-cl-cancellation-poll");
    }

    @Test
    void allegroShipmentShowsTheCarrierByItsNameAndRetriesThroughAllegro() {
        // given
        Order order = order(OrderStatus.Realization);
        Shipment created = order.getShipments().get(0);
        created.setProvider("allegro");
        created.setExternalId("shp-1");
        created.setCarrier("ALLEGRO_ONE_KURIER");
        created.setTrackingNo("A000123456");
        created.setShippedAt(java.time.LocalDateTime.of(2026, 10, 8, 9, 0));
        Shipment failed = new Shipment(ShipmentType.PickupPoint);
        failed.setProvider("allegro");
        failed.setCreation(ShipmentCreationState.pending("cmd-2", java.time.LocalDateTime.now()).failed("Allegro odmówiło"));
        order.addShipment(failed);

        // when
        String card = card(page(render(order, ADMIN)), "przesylki");

        // then
        assertThat(card).contains("One by Allegro")
                .contains("href=\"/dashboard/orders/" + order.getOrderId() + "/shipping?provider=allegro\"");
    }

    @Test
    void aFailedCreationWithOurOwnReasonShowsThatSentence() {
        // given
        Order order = order(OrderStatus.Shipping);
        integrationShipment(order).setCreation(ShipmentCreationState.pending("cmd-1", java.time.LocalDateTime.now())
                .failedWithKey("shipping.creation.unconfirmed"));

        // when
        String card = card(page(render(order, ADMIN)), "przesylki");

        // then
        assertThat(card).contains("<span class=\"cl-status is-warn\">Integracja wysyłki (Furgonetka) nie potwierdziła "
                + "nadania — sprawdź przesyłkę w jej panelu, zanim nadasz ponownie.</span>")
                .doesNotContain("Nie udało się nadać");
    }

    @Test
    void aFailedCreationWithOurOwnCauseReadsLikeTheProvidersReason() {
        // given: POST /packages answered 503, nothing was created
        Order order = order(OrderStatus.Shipping);
        integrationShipment(order).setCreation(ShipmentCreationState.pending("cmd-1", java.time.LocalDateTime.now())
                .failedWithKey("shipping.creation.notCreated"));

        // when
        String card = card(page(render(order, ADMIN)), "przesylki");

        // then
        assertThat(card).contains("<span class=\"cl-status is-warn\">Nie udało się nadać: Integracja wysyłki (Furgonetka) "
                + "nie utworzyła paczki (brak odpowiedzi lub błąd po jej stronie). Nic nie zostało opłacone — spróbuj "
                + "ponownie za chwilę.</span>");
    }

    @Test
    void aFailedPickupWithOurOwnCauseReadsLikeTheProvidersReason() {
        // given
        Order order = order(OrderStatus.Shipping);
        Shipment parcel = integrationShipment(order);
        parcel.setExternalId("21480003");
        parcel.setTrackingNo("0000123");
        parcel.setShippedAt(java.time.LocalDateTime.now().minusHours(1));
        parcel.setPickup(ShipmentPickup.awaiting().failedWithKey("shipping.pickup.immediate.no.windows"));

        // when
        String card = card(page(render(order, ADMIN)), "przesylki");

        // then
        assertThat(card).contains("<span class=\"cl-status is-warn\">Nie udało się zamówić odbioru: Przewoźnik nie podał "
                + "terminu odbioru w najbliższych dniach.</span>");
    }

    @Test
    void aFailedPickupWhoseSentenceStatesTheOutcomeHasNoPrefix() {
        // given
        Order order = order(OrderStatus.Shipping);
        Shipment parcel = integrationShipment(order);
        parcel.setExternalId("21480003");
        parcel.setTrackingNo("0000123");
        parcel.setShippedAt(java.time.LocalDateTime.now().minusHours(1));
        parcel.setPickup(ShipmentPickup.awaiting().failedWithKey("shipping.pickup.not.sent"));

        // when
        String card = card(page(render(order, ADMIN)), "przesylki");

        // then
        assertThat(card).contains("<span class=\"cl-status is-warn\">Odbiór nie został zamówiony — spróbuj ponownie.</span>")
                .doesNotContain("Nie udało się zamówić odbioru");
    }

    @Test
    void aPackageWaitingForPickupOffersItsLabelButNoPickupButton() {
        // given
        Order order = order(OrderStatus.Shipping);
        Shipment parcel = integrationShipment(order);
        parcel.setExternalId("21480003");
        parcel.setTrackingNo("0000123");
        parcel.setShippedAt(java.time.LocalDateTime.now().minusHours(1));
        parcel.setPickup(ShipmentPickup.awaiting());

        // when
        String card = card(page(render(order, ADMIN)), "przesylki");

        // then: the pickup is ordered from the orders list, for all packages at once (client decision 2026-10-07)
        assertThat(card).contains("<span class=\"cl-status is-neutral\">Czeka na odbiór</span>")
                .doesNotContain("/dashboard/shipping/pickups").doesNotContain("Zamów odbiór")
                .contains("href=\"/dashboard/shipping/labels/furgonetka/21480003?back=/dashboard/orders/" + order.getOrderId() + "\"")
                .contains("aria-label=\"Pobierz etykietę przesyłki 1\"")
                .contains(">Edytuj<");
    }

    @Test
    void anOrderedPickupShowsItsDayAndHours() {
        // given
        Order order = order(OrderStatus.Shipping);
        Shipment parcel = integrationShipment(order);
        parcel.setExternalId("21480003");
        parcel.setTrackingNo("0000123");
        parcel.setShippedAt(java.time.LocalDateTime.now().minusHours(1));
        parcel.setPickup(ShipmentPickup.pending("cmd-2", java.time.LocalDateTime.now(), LocalDate.of(2026, 10, 8),
                java.time.LocalTime.of(9, 0), java.time.LocalTime.of(17, 0)).ordered("P-1"));

        // when
        String card = card(page(render(order, ADMIN)), "przesylki");

        // then
        assertThat(card).contains("<span class=\"cl-status is-ok\">Odbiór: czw. 8 paź, 9:00–17:00</span>")
                .doesNotContain("Zamów odbiór");
    }

    @Test
    void aFailedCancellationSendsTheOperatorToTheIntegrationsPanelAndItsRemovalWarnsAboutTheLabel() {
        // given
        Order order = order(OrderStatus.Shipping);
        Shipment sent = order.getShipments().get(0);
        sent.setCarrier("DPD");
        sent.setTrackingNo("T-1");
        sent.setShippedAt(java.time.LocalDateTime.now().minusHours(1));
        sent.setExternalId("EXT-1");
        sent.setCancellation(CourierCancellation.pending("cmd-1", java.time.LocalDateTime.now().minusMinutes(1)));
        sent.setCancellation(sent.getCancellation().failed());

        // when
        String card = card(page(render(order, ADMIN)), "przesylki");

        // then
        assertThat(card).doesNotContain("data-cl-cancellation-poll")
                .contains("<span class=\"cl-status is-bad\">Anulowanie nieudane — sprawdź w panelu integracji wysyłki "
                        + "(Furgonetka)</span>")
                .contains("/cancelShipment\"")
                .contains("data-cl-confirm-message=\"Anulowanie w integracji wysyłki (Furgonetka) nie zostało potwierdzone. "
                        + "Usuń przesyłkę tylko wtedy, gdy etykieta jest anulowana w panelu tej integracji — inaczej kurier "
                        + "może ją nadal odebrać.\"")
                .doesNotContain("shipment-cancel-reason");
    }

    @Test
    void theOnlyShipmentCanBeRemovedCompletelyButABarePlaceholderOffersNoRemove() {
        // given: a number typed by hand on the only shipment; another order with nothing but the delivery choice
        Order order = order(OrderStatus.Realization);
        order.getShipments().get(0).setTrackingNo("T-1");
        String version = OrderShipmentForm.version(order.getShipments().get(0));
        Order bare = order(OrderStatus.Realization);

        // when
        String card = card(page(render(order, ADMIN)), "przesylki");
        String bareCard = card(page(render(bare, ADMIN)), "przesylki");

        // then: removing it takes the customer's delivery choice with it, which the confirmation says
        assertThat(card).contains("aria-label=\"Edytuj przesyłkę 1\"").contains("/shipments/0/remove?version=" + version)
                .doesNotContain("aria-disabled").doesNotContain("remove-reason")
                .contains("data-cl-confirm-message=\"Przesyłka zniknie z zamówienia razem ze sposobem dostawy wybranym przez klienta")
                .contains("data-cl-confirm-action=\"Usuń przesyłkę\"");
        // the placeholder reads as no shipment: greyed, the delivery choice, "Brak przesyłki" and "Uzupełnij"
        assertThat(bareCard).contains("class=\"cl-list-item is-placeholder\"").contains("<span>Sposób dostawy: Kurier</span>")
                .contains("<p class=\"cl-list-desc\">Brak przesyłki</p>")
                .containsPattern("<a class=\"cl-link-button\" href=\"/dashboard/orders/[^\"]+/shipments/0\"\\s+"
                        + "data-cl-dialog-open=\"shipment-dialog-0\" aria-label=\"Uzupełnij przesyłkę 1\">Uzupełnij</a>")
                .doesNotContain("Edytuj przesyłkę 1").doesNotContain("/remove?version=")
                .doesNotContain("aria-label=\"Usuń przesyłkę 1\"").doesNotContain("remove-reason")
                .doesNotContain("zeka na nadanie");
    }

    @Test
    void removingTheOnlyShippedShipmentOfAShippingOrderSaysTheOrderGoesBackToRealization() {
        // given
        Order order = order(OrderStatus.Shipping);
        Shipment only = order.getShipments().get(0);
        only.setType(ShipmentType.PersonalCollection);
        only.setShippedAt(java.time.LocalDateTime.of(2026, 9, 30, 11, 40));

        // when
        String card = card(page(render(order, ADMIN)), "przesylki");

        // then: the dialog's text is honest about the status; the row is an ordinary shipment, not the placeholder
        assertThat(card).contains("a zamówienie wróci do „W realizacji”")
                .doesNotContain("is-placeholder").contains("aria-label=\"Usuń przesyłkę 1\"");
    }

    @Test
    void noShipmentOfADeliveredOrderCanBeRemovedAndTheRowSaysWhy() {
        // given
        Order order = order(OrderStatus.Delivered);
        order.addShipment(new Shipment(ShipmentType.Courier));

        // when
        String card = card(page(render(order, ADMIN)), "przesylki");

        // then
        assertThat(card).doesNotContain("/remove")
                .containsPattern("<button type=\"button\" class=\"cl-link-button\" aria-disabled=\"true\"[^>]*aria-label=\"Usuń przesyłkę 1\"[^>]*aria-describedby=\"shipment-1-remove-reason\"")
                .contains("id=\"shipment-1-remove-reason\">Zamówienie jest dostarczone — przesyłek nie usuniesz.</p>")
                .contains("id=\"shipment-2-remove-reason\">Zamówienie jest dostarczone — przesyłek nie usuniesz.</p>");
    }

    @Test
    void aShipmentWithADeliveryDateCannotBeRemovedAndTheOthersCan() {
        // given
        Order order = order(OrderStatus.Shipping);
        order.getShipments().get(0).setDeliveredAt(LocalDateTime.of(2026, 9, 28, 0, 0));
        order.addShipment(new Shipment(ShipmentType.Courier));

        // when
        String card = card(page(render(order, ADMIN)), "przesylki");

        // then
        assertThat(card).doesNotContain("/shipments/0/remove").contains("/shipments/1/remove?version=")
                .contains("id=\"shipment-1-remove-reason\">Przesyłka ma datę dostarczenia — nie usuniesz jej.</p>")
                .doesNotContain("shipment-2-remove-reason");
    }

    @Test
    void aDateEnteredWithoutATimeShowsAsTheDateAloneAndATrackedTimeStays() {
        // given
        Order order = order(OrderStatus.Shipping);
        order.getShipments().get(0).setShippedAt(LocalDateTime.of(2026, 9, 27, 0, 0));
        order.getShipments().get(0).setDeliveredAt(LocalDateTime.of(2026, 9, 28, 13, 20));

        // when
        String card = card(page(render(order, ADMIN)), "przesylki");

        // then
        assertThat(card).contains("27.09.2026").doesNotContain("27.09.2026, 00:00").contains("28.09.2026, 13:20");
    }

    @Test
    void aShipmentDialogHoldsOneShipmentWithDatesOnly() {
        // given: the operator does not need the hour; a same-day save keeps the time the tracking stored
        Order order = order(OrderStatus.Realization);
        Shipment shipment = order.getShipments().get(0);
        shipment.setCarrier("DPD");
        shipment.setShippedAt(LocalDateTime.of(2026, 9, 27, 10, 30, 12));

        // when
        String dialog = dialog(page(render(order, ADMIN)), "shipment-dialog-0");

        // then
        assertThat(dialog).startsWith("id=\"shipment-dialog-0\"").doesNotContain("<table").doesNotContain("datetime-local")
                .contains(">Przesyłka 1<").contains("name=\"index\" value=\"0\"")
                .contains("name=\"version\" value=\"" + OrderShipmentForm.version(shipment) + "\"")
                .containsPattern("<option[^>]*value=\"Courier\"[^>]*selected[^>]*>Kurier</option>")
                .contains("for=\"shipment-0-carrierSelect\"").contains("aria-label=\"Nazwa innego przewoźnika\"")
                .containsPattern("type=\"date\" id=\"shipment-0-shippedDate\" name=\"shippedDate\" value=\"2026-09-27\"")
                .containsPattern("type=\"date\" id=\"shipment-0-deliveredDate\" name=\"deliveredDate\"")
                .doesNotContain("shippedTime").doesNotContain("deliveredTime").doesNotContain("Godzina")
                .doesNotContain("cl-date-time").contains(">Zapisz przesyłkę<")
                .contains("data-cl-dialog-close-on-success=\"true\"");
        for (String field : List.of("type", "trackingNo", "collectionPointCode", "trackingUrl", "shippedDate", "deliveredDate")) {
            assertThat(dialog).contains("for=\"shipment-0-" + field + "\"").contains("id=\"shipment-0-" + field + "\"");
        }
    }

    @Test
    void aCourierShipmentDialogShowsItsCarrierAndNumberReadOnlyAndNoDateAfterToday() {
        // given: the carrier gave the number with the courier order
        Order order = order(OrderStatus.Shipping);
        Shipment shipment = order.getShipments().get(0);
        shipment.setCarrier("DPD");
        shipment.setTrackingNo("T-1");
        shipment.setShippedAt(LocalDateTime.of(2026, 9, 27, 10, 30));
        shipment.setExternalId("EXT-1");
        String today = java.time.LocalDate.now(OrderShipmentForm.OPERATOR_ZONE).toString();

        // when
        String dialog = dialog(page(render(order, ADMIN)), "shipment-dialog-0");

        // then: no picker to change the carrier with, both fields posted as they are, the reason read with them
        assertThat(dialog).doesNotContain("shipment-0-carrierSelect")
                .containsPattern("id=\"shipment-0-carrier\"[^>]*aria-describedby=\"shipment-0-courierLocked\"[^>]*readonly")
                .containsPattern("id=\"shipment-0-trackingNo\"[^>]*value=\"T-1\"[^>]*aria-describedby=\"shipment-0-courierLocked\"[^>]*readonly")
                .contains("id=\"shipment-0-courierLocked\">Numer nadał przewoźnik — typ, przewoźnika i numer zmienisz, anulując przesyłkę.</p>")
                .containsPattern("<select[^>]*id=\"shipment-0-type\"[^>]*aria-describedby=\"shipment-0-courierLocked\"[^>]*disabled")
                .contains("<input type=\"hidden\" name=\"type\" value=\"Courier\">")
                .doesNotContainPattern("id=\"shipment-0-collectionPointCode\"[^>]*readonly")
                .containsPattern("id=\"shipment-0-shippedDate\"[^>]*max=\"" + today + "\"")
                .containsPattern("id=\"shipment-0-deliveredDate\"[^>]*max=\"" + today + "\"");
    }

    @Test
    void theNewShipmentDialogIsBlankAndPostsNoIndex() {
        // given
        Order order = order(OrderStatus.Realization);
        order.setShipments(new ArrayList<>());

        // when
        String dialog = dialog(page(render(order, ADMIN)), "shipment-dialog-new");

        // then
        assertThat(dialog).contains(">Nowa przesyłka<").doesNotContain("name=\"index\"").doesNotContain("name=\"version\"")
                .contains("name=\"trackingNo\"").contains(">Dodaj przesyłkę<");
    }

    @Test
    void aReadOnlyPageHasNeitherShipmentActionsNorDialogs() {
        // when
        String html = page(render(order(OrderStatus.Realization), SUPER_ADMIN));

        // then
        assertThat(html).doesNotContain("shipment-dialog-").doesNotContain("Dodaj przesyłkę")
                .doesNotContain("Edytuj przesyłkę");
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
    void theSerialNumbersDialogNamesEachItemsDelivery() {
        // given: two delivered pieces of one product from different deliveries
        Order order = order(OrderStatus.Delivered);
        OrderItem first = new OrderItem(order.getOrderId(), "CPU", "AMD Ryzen 7 9800X3D", 1, 749, "SKU-1", false, 0);
        first.setStatus(FulfilmentStatus.Delivered);
        first.setDeliveryId("abcd1234-0000-0000-0000-000000000001");
        OrderItem second = new OrderItem(order.getOrderId(), "CPU", "AMD Ryzen 7 9800X3D", 1, 749, "SKU-1", false, 1);
        second.setStatus(FulfilmentStatus.Delivered);

        // when
        String html = page(render(order, List.of(first, second), ADMIN, Set.of()));

        // then
        Matcher dialog = Pattern.compile("(?s)id=\"serials-dialog\"(.*?)</dialog>").matcher(html);
        assertThat(dialog.find()).isTrue();
        assertThat(dialog.group(1)).contains("<td data-label=\"Kod producenta\">")
                .contains("<td class=\"is-numeric\" data-label=\"Ilość\">1</td>")
                .contains("<td data-label=\"Nr dostawy\">—</td>").contains("<td data-label=\"Nr seryjny\">");
        assertThat(dialog.group(1)).contains("<th scope=\"col\">Nr dostawy</th>")
                .contains("<td data-label=\"Nr dostawy\">" + first.getShortenedDeliveryId() + "</td>");
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
    void aCompletedOrderShowsItsSettingsReadOnly() {
        // when
        String html = page(render(order(OrderStatus.Completed), ADMIN));

        // then
        assertThat(html).doesNotContain("id=\"order-settings-form\"").doesNotContain("settings-dialog")
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
    void theAddressEditLinksOpenDialogsAndStillLeadToTheAddressPage() {
        // given
        Order order = order(OrderStatus.Assembly);
        order.getBillingDetails().setCity("Warszawa");
        order.getBillingDetails().setTaxId("5250000000");
        ShippingDetails shipping = new ShippingDetails();
        shipping.setName("Anna");
        shipping.setCity("Kraków");
        order.setShippingDetails(shipping);

        // when
        String html = page(render(order, ADMIN));

        // then: each "Edytuj" is a link to the page, enhanced to open its own dialog
        assertThat(html).containsPattern("href=\"/dashboard/orders/3e373abc-1111-2222-3333-444455556666/address\\?type=billing\"[^>]*data-cl-dialog-open=\"address-dialog-billing\"")
                .containsPattern("href=\"/dashboard/orders/3e373abc-1111-2222-3333-444455556666/address\\?type=shipping\"[^>]*data-cl-dialog-open=\"address-dialog-shipping\"");
        String billing = dialog(html, "address-dialog-billing");
        assertThat(html).contains("<dialog class=\"cl-dialog is-form\" id=\"address-dialog-billing\"");
        assertThat(billing).contains("aria-labelledby=\"address-billing-title\"")
                .contains("id=\"address-billing-title\"").contains("Edytuj dane rozliczeniowe")
                .contains("id=\"address-form-billing\"").contains("novalidate").contains("data-cl-async")
                .contains("data-cl-dialog-close-on-success=\"true\"")
                .contains("action=\"/dashboard/orders/3e373abc-1111-2222-3333-444455556666/updateAddressDetails?type=billing\"")
                .contains("for=\"billingDetails.name\"").contains("id=\"billingDetails.name\" name=\"billingDetails.name\" type=\"text\" value=\"Piotr\"")
                .contains("name=\"billingDetails.city\" type=\"text\" value=\"Warszawa\"")
                .contains("name=\"billingDetails.taxId\" type=\"text\" value=\"5250000000\"")
                .contains("autocomplete=\"off\"").contains("data-cl-dialog-close")
                .doesNotContain("data-cl-error-summary").doesNotContain("aria-invalid");
        String shippingDialog = dialog(html, "address-dialog-shipping");
        assertThat(shippingDialog).contains("Edytuj adres wysyłki").contains("id=\"address-form-shipping\"")
                .contains("name=\"shippingDetails.name\" type=\"text\" value=\"Anna\"")
                .contains("name=\"shippingDetails.city\" type=\"text\" value=\"Kraków\"")
                .contains("name=\"shippingDetails.companyName\"").doesNotContain("taxId");
    }

    @Test
    void aScriptTypedIntoTheAddressIsPrintedAsTextInTheCardAndTheDialog() {
        // given
        Order order = order(OrderStatus.Assembly);
        order.getBillingDetails().setCity("<script>alert(1)</script>");

        // when
        String html = page(render(order, ADMIN));

        // then
        assertThat(html).doesNotContain("<script>alert(1)</script>")
                .contains("&lt;script&gt;alert(1)&lt;/script&gt;")
                .contains("name=\"billingDetails.city\" type=\"text\" value=\"&lt;script&gt;alert(1)&lt;/script&gt;\"");
    }

    @Test
    void aLockedAddressHasNeitherEditLinkNorDialog() {
        // given
        Order invoiced = order(OrderStatus.Delivered);
        invoiced.addDocument(new Document("fv", "FV/1", null, DocumentType.InvoiceVat));
        Order labelled = order(OrderStatus.Realization);
        Shipment parcel = new Shipment(ShipmentType.Courier);
        parcel.setTrackingNo("T-1");
        labelled.setShipments(new ArrayList<>(List.of(parcel)));

        // when
        String invoicedHtml = page(render(invoiced, ADMIN));
        String labelledHtml = page(render(labelled, ADMIN));
        String closedHtml = page(render(order(OrderStatus.Completed), ADMIN));
        String superAdminHtml = page(render(order(OrderStatus.Assembly), SUPER_ADMIN));

        // then
        assertThat(invoicedHtml).doesNotContain("address-dialog-billing").contains("address-dialog-shipping")
                .contains("Zamówienie ma już fakturę albo paragon — danych rozliczeniowych nie zmienisz.");
        assertThat(labelledHtml).doesNotContain("address-dialog-shipping").contains("address-dialog-billing")
                .contains("Etykieta już nadana — adresu wysyłki nie zmienisz.");
        assertThat(closedHtml).doesNotContain("address-dialog").doesNotContain("updateAddressDetails");
        assertThat(superAdminHtml).doesNotContain("address-dialog").doesNotContain("updateAddressDetails");
    }

    @Test
    void theAsyncAddressAnswerShowsEachErrorNextToItsFieldAndASummary() {
        // given: on the async path the model attribute "order" is the posted form, so the fragment reads only address
        OrderAddressForm form = OrderAddressForm.shipping("3e373abc-1111-2222-3333-444455556666", new ShippingDetails());
        Map<String, Object> variables = new HashMap<>();
        variables.put("order", new Object());
        variables.put("address", form.withErrors(form.validate()));

        // when
        String html = SettingsTemplateRenderer.render("<div th:replace=\"~{orders/details/address :: dialogForm}\"></div>", variables);

        // then
        assertThat(html).startsWith("<form").contains("id=\"address-form-shipping\"")
                .contains("data-cl-dialog-close-on-success=\"true\"").contains("id=\"address-shipping-title\"")
                .contains("id=\"address-shipping-errors\"").contains("data-cl-error-summary")
                .contains("href=\"#shippingDetails.name\"")
                .containsPattern("id=\"shippingDetails.name\"[^>]*aria-invalid=\"true\"[^>]*aria-describedby=\"shippingDetails.name-error\"")
                .contains("id=\"shippingDetails.name-error\"").contains("Imię jest wymagane")
                .contains("Telefon jest wymagany")
                .contains("Dwuliterowy kod, np. PL.").doesNotContain("wielkimi literami").doesNotContain("??");
        // the surname is optional: seven required fields
        assertThat(occurrences(html, "class=\"cl-field-error\"")).isEqualTo(7);
    }

    @Test
    void aRefusedAsyncAddressAnswerNamesTheReasonAboveTheFields() {
        // given
        Map<String, Object> variables = new HashMap<>();
        variables.put("address", OrderAddressForm.billing("3e373abc-1111-2222-3333-444455556666", new BillingDetails())
                .withRefusal("Po wystawieniu faktury danych rozliczeniowych nie zmienisz."));

        // when
        String html = SettingsTemplateRenderer.render("<div th:replace=\"~{orders/details/address :: dialogForm}\"></div>", variables);

        // then
        assertThat(html).contains("data-cl-error-summary").contains("Po wystawieniu faktury danych rozliczeniowych nie zmienisz.")
                .doesNotContain("aria-invalid");
    }

    @Test
    void theAddressPageWithoutJavascriptPostsTheSameFormAndCancelsBackToTheOrder() {
        // given
        Map<String, Object> variables = new HashMap<>();
        variables.put("navigation", null);
        variables.put("shortId", "3e373abc");
        variables.put("address", OrderAddressForm.billing("3e373abc-1111-2222-3333-444455556666", order(OrderStatus.New).getBillingDetails()));

        // when
        String html = page(SettingsTemplateRenderer.render("orders/address", variables));

        // then
        assertThat(html).contains("id=\"address-form-billing\"")
                .contains("action=\"/dashboard/orders/3e373abc-1111-2222-3333-444455556666/updateAddressDetails?type=billing\"")
                .contains("href=\"/dashboard/orders/3e373abc-1111-2222-3333-444455556666\"")
                .contains("Dane rozliczeniowe zamówienia 3e373abc").contains("value=\"Piotr\"")
                .doesNotContain("data-cl-dialog-close-on-success").doesNotContain("data-cl-dialog-close")
                .doesNotContain("class=\"input").doesNotContain("class=\"box").doesNotContain("??");
    }

    @Test
    void everyOptionalMarkerOfTheFormDialogsIsSeparatedFromItsLabelByASpace() {
        // when
        String html = page(render(order(OrderStatus.Assembly), ADMIN));

        // then: the marker's dot comes from CSS, the space before it only from the markup
        assertThat(html).contains("<span class=\"cl-optional\">opcjonalne</span>")
                .doesNotContain("</span><span class=\"cl-optional\"");
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
        assertThat(completed).doesNotContain("Usuniesz tylko nowe zamówienie");
        assertThat(open).contains("Usuniesz tylko nowe zamówienie");
    }

    @Test
    void bothAddressBlocksPrintInFullWhenTheyAreTheSame() {
        // given: the shipping address, e-mail and phone equal the billing ones
        Order order = order(OrderStatus.Assembly);
        order.getBillingDetails().setStreetAndNumber("ul. Przykładowa 5");
        order.getBillingDetails().setPostalCode("00-001");
        order.getBillingDetails().setCity("Warszawa");
        order.getBillingDetails().setEmail("piotr@example.com");
        order.getBillingDetails().setPhone("+48 600 700 800");
        ShippingDetails shipping = new ShippingDetails();
        shipping.setName("Piotr");
        shipping.setSurname("Wiśniewski");
        shipping.setStreetAndNumber("ul. Przykładowa 5");
        shipping.setPostalCode("00-001");
        shipping.setCity("Warszawa");
        shipping.setEmail("piotr@example.com");
        shipping.setPhone("+48 600 700 800");
        order.setShippingDetails(shipping);

        // when
        String html = page(render(order, ADMIN));

        // then
        String card = html.substring(html.indexOf("id=\"customer-title\""), html.indexOf("</section>", html.indexOf("id=\"customer-title\"")));
        assertThat(card).doesNotContain("jak dane rozliczeniowe");
        assertThat(occurrences(card, "ul. Przykładowa 5")).isEqualTo(2);
        assertThat(occurrences(card, "00-001 Warszawa")).isEqualTo(2);
        assertThat(occurrences(card, "href=\"mailto:piotr@example.com\"")).isEqualTo(2);
        assertThat(occurrences(card, "href=\"tel:+48 600 700 800\"")).isEqualTo(2);
    }

    @Test
    void everyEmailOfTheCustomerCardCopiesWithANamedButton() {
        // given
        Order order = order(OrderStatus.Assembly);
        order.getBillingDetails().setEmail("piotr@example.com");
        ShippingDetails shipping = new ShippingDetails();
        shipping.setName("Anna");
        shipping.setEmail("odbiorca@example.com");
        order.setShippingDetails(shipping);

        // when
        String html = page(render(order, ADMIN));

        // then: the mailto link stays and an icon-only copy button follows it, glued to it by a word joiner, its
        // glyph hidden
        assertThat(html).contains("<a href=\"mailto:piotr@example.com\">piotr@example.com</a>&#8288;<button type=\"button\" class=\"cl-copy-inline is-icon\" data-cl-copy=\"piotr@example.com\" aria-label=\"Kopiuj e-mail piotr@example.com\"><span class=\"cl-copy-icon\" aria-hidden=\"true\"><i class=\"far fa-copy\"></i></span></button>")
                .contains("data-cl-copy=\"odbiorca@example.com\" aria-label=\"Kopiuj e-mail odbiorca@example.com\"");
    }

    @Test
    void aSuperAdminSeesTheCostSectionAndItemCostButNoActions() {
        // when
        String html = page(render(order(OrderStatus.New), SUPER_ADMIN));

        // then
        assertThat(html).contains("id=\"finances-costs\"").contains("<summary>Koszt i zysk</summary>").contains("Koszt: 712,17 PLN brutto / szt.")
                .doesNotContain("data-cl-dialog-open").doesNotContain("item-menu-");
    }

    @Test
    void aPaymentShowsItsBankDateEvenWithoutAnOperationNumber() {
        // given
        Order order = order(OrderStatus.Realization);
        Payment payment = new Payment("REF-2", "Jan Kowalski", PaymentSource.BankTransfer, 300, 0);
        payment.setBankTransactionDate(LocalDate.of(2026, 9, 20));
        order.addPayment(payment);

        // when
        String html = page(render(order, ADMIN));

        // then
        assertThat(html).contains("data operacji 20.09.2026").doesNotContain("operacja null");
    }

    @Test
    void anOpenOrderShowsNoClosingStrip() {
        // given: an open order that misses a payment, the WZ and the review
        Order order = order(OrderStatus.Assembly);

        // when
        String html = page(render(order, ADMIN));

        // then: the client asked for the strip to go; the cards keep their anchors
        assertThat(html).doesNotContain("Do zamknięcia").doesNotContain("closing-title").doesNotContain("cl-closing")
                .doesNotContain("cl-doc-marks").doesNotContain("Warunki zamknięcia zamówienia")
                .contains("id=\"platnosci\"").contains("id=\"dokumenty\"").contains("id=\"przesylki\"");
    }

    @Test
    void aSuperAdminIsNotToldWhichDocumentToIssueNext() {
        // when
        String html = page(render(b2bOrder(OrderStatus.New), SUPER_ADMIN));

        // then
        assertThat(html).doesNotContain("Następny do wystawienia").contains("Brak faktury ani paragonu.");
    }

    @Test
    void dropshipItemsGreyOutWarehouseMovesAndAddingItems() {
        // given (intent of DropshipTemplateTest#orderDetailsGreysOutWarehouseMoves… and …DisablesAddingItems…)
        Order order = order(OrderStatus.Assembly);
        List<OrderItem> items = items(order);

        // when
        String html = page(render(order, items, ADMIN, Set.of(items.get(0).getItemId())));

        // then: the menu entry stays readable and focusable, greyed through aria-disabled like the row menu's entries
        assertThat(html).containsPattern("data-cl-bulk-scope=\"allocated-product\"[^>]*aria-disabled=\"true\"")
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
    void bothPrintoutsInTheMenuPrintFromAFrameAndStillLinkToTheirSheet() {
        // given
        Order order = order(OrderStatus.Assembly);

        // when
        String html = render(order, ADMIN);

        // then: print.js prints the linked sheet in place; without it the href still opens the sheet
        String base = "/dashboard/orders/" + order.getOrderId();
        assertThat(html).containsPattern("<a class=\"cl-menu-item\" href=\"" + base + "/card\" data-cl-print-frame>Drukuj kartę zamówienia</a>")
                .containsPattern("<a class=\"cl-menu-item\" href=\"" + base + "/collection\" data-cl-print-frame>Drukuj protokół odbioru</a>")
                .contains("/js/print.js");
    }

    @Test
    void theSelectionBarCarriesTheScopeTemplateAndThePageLoadsTheItemsScript() {
        // when
        String html = render(order(OrderStatus.New), ADMIN);

        // then
        assertThat(html).contains("data-cl-scope-template=\"{label} ({n} z {m})\"").contains("/js/order-items.js")
                .contains("data-cl-bulk-confirm-message=\"Zamówione i przyjęte pozycje wrócą na stan magazynu");
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
    void theAddPaymentDialogPostsToTheOrderAndTheWholeListDialogIsGone() {
        // given
        Order order = order(OrderStatus.Realization);
        order.addPayment(new Payment("REF-1", "Jan Kowalski", PaymentSource.BankTransfer, 500, 2));

        // when
        String html = page(render(order, ADMIN));

        // then
        assertThat(html).contains("id=\"addPaymentModal\"")
                .contains("action=\"/dashboard/orders/3e373abc-1111-2222-3333-444455556666/addPayment\"")
                .contains(">Przelew bankowy</option>").doesNotContain("addEmptyPaymentRow")
                .doesNotContain("payments-edit-dialog").doesNotContain("updatePayments").doesNotContain("payments[0]")
                .doesNotContain("Edytuj płatności").doesNotContain("order-payments.js");
    }

    @Test
    void eachPaymentHasEditAndRemoveAndTheCardHeadAddsOne() {
        // given
        Order order = order(OrderStatus.Realization);
        Payment first = new Payment("REF-1", "Jan Kowalski", PaymentSource.BankTransfer, 500, 2);
        order.addPayment(first);
        order.addPayment(new Payment("REF-2", "Jan Kowalski", PaymentSource.Card, 100, 0));
        String prefix = "/dashboard/orders/" + order.getOrderId() + "/payments/";

        // when
        String card = card(page(render(order, ADMIN)), "platnosci");

        // then
        assertThat(card).contains("data-cl-dialog-open=\"addPaymentModal\"").contains(">Dodaj wpłatę<")
                .contains("href=\"" + prefix + "0\"").contains("data-cl-dialog-open=\"payment-dialog-0\"")
                .contains("aria-label=\"Edytuj płatność 1\"").contains("aria-label=\"Edytuj płatność 2\"")
                .contains(prefix + "0/remove?version=" + OrderPaymentForm.version(first))
                .contains("aria-label=\"Usuń płatność 2\"").contains("data-cl-confirm-title=\"Usunąć płatność 2?\"")
                .contains("data-cl-confirm-message=\"Płatność zniknie z zamówienia, a kwota do zapłaty przeliczy się od nowa.\"")
                .doesNotContain("aria-disabled").doesNotContain("remove-reason")
                .contains("Wpłacono:");
    }

    @Test
    void thePendingPaymentIsEditedButItsRemoveIsGreyedWithTheReason() {
        // given
        Order order = order(OrderStatus.New);
        order.addPayment(new Payment(PaymentSource.BankTransfer));

        // when
        String card = card(page(render(order, ADMIN)), "platnosci");

        // then
        assertThat(card).contains("aria-label=\"Edytuj płatność 1\"").doesNotContain("/remove")
                .containsPattern("<button type=\"button\" class=\"cl-link-button\" aria-disabled=\"true\"[^>]*aria-label=\"Usuń płatność 1\"[^>]*aria-describedby=\"payment-1-remove-reason\"")
                .contains("id=\"payment-1-remove-reason\">Oczekiwana wpłata zniknie sama po dodaniu wpłaty.</p>");
    }

    @Test
    void removingTheOnlySettledPaymentIsConfirmedWithThePendingOneThatStays() {
        // given
        Order order = order(OrderStatus.Realization);
        order.addPayment(Payment.bankTransfer("REF-1", "Jan", 100));

        // when
        String card = card(page(render(order, ADMIN)), "platnosci");

        // then
        assertThat(card).contains("Zostanie oczekiwana płatność tą samą metodą.");
    }

    @Test
    void aPaymentDialogHoldsOnePaymentWithLabelledFieldsOfItsOwn() {
        // given
        Order order = order(OrderStatus.Realization);
        order.addPayment(Payment.bankTransfer("REF-0", "Jan", 50));
        Payment payment = new Payment("REF-1", "Jan Kowalski", PaymentSource.Card, 500, 2.5);
        payment.setBankTransactionNo("OP-1");
        payment.setBankTransactionDate(LocalDate.of(2026, 9, 20));
        order.addPayment(payment);

        // when
        String dialog = dialog(page(render(order, ADMIN)), "payment-dialog-1");

        // then
        assertThat(dialog).startsWith("id=\"payment-dialog-1\"").doesNotContain("<table").doesNotContain("is-wide")
                .contains(">Płatność 2<").contains("name=\"index\" value=\"1\"")
                .contains("name=\"version\" value=\"" + OrderPaymentForm.version(payment) + "\"")
                .contains("action=\"/dashboard/orders/3e373abc-1111-2222-3333-444455556666/payments\"")
                .contains("data-cl-async").contains("data-cl-dialog-close-on-success=\"true\"")
                .containsPattern("<option[^>]*value=\"Card\"[^>]*selected[^>]*>")
                .containsPattern("type=\"text\" id=\"payment-1-amount\" name=\"amount\"\\s+value=\"500.00\"")
                .doesNotContain("step=").doesNotContain("type=\"number\"").contains(">Kwota<")
                .containsPattern("id=\"payment-1-fee\" name=\"fee\"\\s+value=\"2.50\"")
                .containsPattern("type=\"date\" id=\"payment-1-bankTransactionDate\" name=\"bankTransactionDate\"\\s+value=\"2026-09-20\"")
                .contains("inputmode=\"decimal\"").contains(">Zapisz płatność<")
                .contains("Zmiana kwoty przelicza „Do zapłaty”").doesNotContain("name=\"direction\"")
                .doesNotContain("Przy kwocie 0");
        for (String field : List.of("source", "name", "amount", "fee", "referenceNo", "bankTransactionNo", "bankTransactionDate")) {
            assertThat(dialog).contains("for=\"payment-1-" + field + "\"").contains("id=\"payment-1-" + field + "\"");
        }
        assertThat(dialog).containsPattern("class=\"cl-span-3[^\"]*\"");
    }

    @Test
    void thePendingPaymentDialogSaysZeroKeepsItPending() {
        // given
        Order order = order(OrderStatus.New);
        order.addPayment(new Payment(PaymentSource.CashOnDelivery));

        // when
        String dialog = dialog(page(render(order, ADMIN)), "payment-dialog-0");

        // then
        assertThat(dialog).contains("Przy kwocie 0 płatność zostaje oczekiwana")
                .containsPattern("<option[^>]*value=\"CashOnDelivery\"[^>]*selected");
    }

    @Test
    void aReadOnlyPageHasNeitherPaymentActionsNorDialogs() {
        // given
        Order order = order(OrderStatus.Completed);
        order.addPayment(Payment.bankTransfer("REF-1", "Jan", 100));

        // when
        String html = page(render(order, ADMIN));
        String superAdmin = page(render(order(OrderStatus.Realization), SUPER_ADMIN));

        // then
        assertThat(card(html, "platnosci")).contains("REF-1").doesNotContain("Edytuj płatność")
                .doesNotContain("Usuń płatność").doesNotContain("Dodaj wpłatę");
        assertThat(html).doesNotContain("payment-dialog-");
        assertThat(superAdmin).doesNotContain("payment-dialog-").doesNotContain("Edytuj płatność");
    }

    @Test
    void thePaymentPageWithoutJavascriptPostsTheSameFormAndCancelsBackToTheOrder() {
        // given
        Order order = order(OrderStatus.Realization);
        order.addPayment(Payment.bankTransfer("REF-1", "Jan", 100));
        Map<String, Object> variables = subpageVariables(order);
        variables.put("payment", OrderPaymentForm.of(order.getOrderId(), 0, order.getPayments().get(0)));

        // when
        String html = SettingsTemplateRenderer.render("orders/payment", variables);

        // then
        assertThat(html).contains("Płatność 1 zamówienia 3e373abc")
                .contains("action=\"/dashboard/orders/3e373abc-1111-2222-3333-444455556666/payments\"")
                .contains("href=\"/dashboard/orders/3e373abc-1111-2222-3333-444455556666\"")
                .contains("id=\"payment-0-amount\"").contains(">Zapisz płatność<")
                .doesNotContain("data-cl-dialog-close-on-success").doesNotContain("data-cl-dialog-close")
                .doesNotContain("??");
    }

    @Test
    void aRefusedPaymentFormOffersToReloadThePage() {
        // given
        Order order = order(OrderStatus.Realization);
        order.addPayment(Payment.bankTransfer("REF-1", "Jan", 100));
        Map<String, Object> variables = new HashMap<>();
        variables.put("payment", OrderPaymentForm.of(order.getOrderId(), 0, order.getPayments().get(0))
                .withRefusal("Płatność zmieniła się w międzyczasie."));

        // when
        String html = SettingsTemplateRenderer.render("<div th:replace=\"~{orders/details/payments :: dialogForm}\"></div>", variables);

        // then
        assertThat(html).startsWith("<form").contains("Płatność zmieniła się w międzyczasie.")
                .contains(">Odśwież stronę</a>").contains("data-cl-dialog-close-on-success=\"true\"");
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
        // overload (resolveFor(Order, OrderItem, DropshipAssessment)) must be used, not resolveFor(OrderItem) alone.
        // The two only diverge for a DirectToConsumer order with an item still awaiting delivery (New/Allocation,
        // unclaimed) at a supplier the dropship assessment accepts — there the order-aware resolver sends the
        // operator to the dropship confirmation page instead of "create a warehouse delivery".
        Order order = order(OrderStatus.Assembly);
        order.setFulfilmentType(FulfilmentType.DirectToConsumer);
        OrderItem awaiting = inDelivery(order, "Acme", FulfilmentStatus.Allocation);
        DeliveryRedirectResolver resolver = new DeliveryRedirectResolver();
        String expected = resolver.resolveFor(order, awaiting,
                pl.commercelink.inventory.deliveries.DropshipAssessment.of(List.of("Acme")));
        String itemOnly = resolver.resolveFor(awaiting);
        // the fixture is only useful if the two overloads actually disagree
        assertThat(expected).contains("/dashboard/deliveries/create/Acme?order=");
        assertThat(expected).isNotEqualTo(itemOnly);

        // when
        String html = page(render(order, List.of(awaiting), ADMIN, Set.of()));

        // then: the rendered link is the order-aware one, not the item-only fallback a regression would produce
        assertThat(html).contains("<a class=\"cl-status").contains("href=\"" + expected.replace("&", "&amp;") + "\"")
                .doesNotContain("href=\"" + itemOnly.replace("&", "&amp;") + "\"");
    }

    @Test
    void itemsInADropshipDeliveryGreyOutWarehouseMovesAndAddingItemsWithTheReason() {
        // given (was DropshipTemplateTest#orderDetailsGreysOutWarehouseMoves…, #…DisablesAddingItems…)
        Order order = order(OrderStatus.Assembly);
        OrderItem dropship = inDelivery(order, "delivery-9", FulfilmentStatus.Ordered);

        // when
        String html = page(render(order, List.of(dropship), ADMIN, Set.of(dropship.getItemId())));

        // then: the bulk actions are greyed through aria-disabled (menu entries and "Remove" stay focusable with
        // their reason); so is the add-items button, which opens nothing
        assertThat(html).containsPattern("data-cl-bulk-action=\"[^\"]*moveSelectedItemsToTheWarehouse\"[^>]*aria-disabled=\"true\"")
                // "Do alokacji" is the item's own entry now, greyed with the order's dropship reason
                .containsPattern("<span>Do alokacji</span>\\s*<span class=\"cl-menu-reason\">Zamówienie ma pozycje w dostawie dropship</span>")
                // the dropship lock disables every bulk action, not only the two above
                .containsPattern("data-cl-bulk-action=\"[^\"]*moveSelectedItemsToTheWarehouseForRMA\"[^>]*aria-disabled=\"true\"")
                .containsPattern("data-cl-bulk-action=\"[^\"]*removeSelectedItemsFromOrder\"[^>]*aria-disabled=\"true\"")
                .containsPattern("<button type=\"button\" class=\"cl-button\" aria-disabled=\"true\"[^>]*aria-describedby=\"add-items-reason\"")
                .doesNotContain("data-cl-dialog-open=\"item-add-dialog\"")
                .contains("id=\"add-items-reason\"");
        // the add-items button stays focusable, described by its reason, and is not a natively disabled button
        String head = html.substring(html.indexOf("id=\"pozycje\""), html.indexOf("id=\"add-items-reason\""));
        assertThat(head).doesNotContain("disabled=\"disabled\"");
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
        assertThat(fallback).contains("formaction=\"/dashboard/orders/3e373abc-1111-2222-3333-444455556666/bulk-confirm?action=TO_WAREHOUSE\"")
                .doesNotContain("action=ALLOCATE")
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
        assertThat(withTracking).contains("Śledzona (Furgonetka)");
        assertThat(withoutTracking).doesNotContain("Śledzona");
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
    void aDocumentLinkWithAScriptSchemeIsPrintedAsTextNotALink() {
        // given
        Order order = order(OrderStatus.Delivered);
        order.addDocument(new Document("r1", "PAR/1", "javascript:alert(1)", DocumentType.Receipt));

        // when
        String html = page(render(order, ADMIN));

        // then
        assertThat(html).doesNotContain("href=\"javascript:");
        assertThat(html).contains("<span>PAR/1</span>");
    }

    @Test
    void eachDocumentRowNamesItsKindInOneWordBeforeTheNumber() {
        // given
        Order order = order(OrderStatus.Delivered);
        order.addDocument(new Document("pf", "PF/1", null, DocumentType.Proforma));
        order.addDocument(new Document("fz", "FZ/1", null, DocumentType.InvoiceAdvance));
        order.addDocument(new Document("fv", "FV/1", null, DocumentType.InvoiceVat));
        order.addDocument(new Document("fi", "FI/1", null, DocumentType.InvoicePersonal));
        order.addDocument(new Document("par", "PAR/1", null, DocumentType.Receipt));
        order.addDocument(new Document("wz", "WZ/MAG/2026/000001", null, DocumentType.GoodsIssue));

        // when
        String html = page(render(order, ADMIN));
        String documents = html.substring(html.indexOf("id=\"dokumenty\""), html.indexOf("id=\"platnosci\""));

        // then: "Faktura" for every invoice, "Paragon", "Dokument" for the warehouse note; a pro forma keeps its name
        assertThat(documents).containsPattern("<span>Proforma</span>\\s*<span>PF/1</span>")
                .containsPattern("<span>Faktura</span>\\s*<span>FZ/1</span>")
                .containsPattern("<span>Faktura</span>\\s*<span>FV/1</span>")
                .containsPattern("<span>Faktura</span>\\s*<span>FI/1</span>")
                .containsPattern("<span>Paragon</span>\\s*<span>PAR/1</span>")
                .containsPattern("<span>Dokument</span>\\s*(<a [^>]*>)?<span>WZ/MAG/2026/000001</span>")
                .doesNotContain("<span>WZ</span>").doesNotContain("<span>Faktura VAT</span>");
    }

    @Test
    void documentNumberLinkCarriesItsIconInside() {
        // given
        Order order = order(OrderStatus.Delivered);
        order.addDocument(new Document("fv", "FV/2026/09/118", "https://faktury.example/118", DocumentType.InvoiceVat));

        // when
        String html = page(render(order, ADMIN));

        // then: the new-tab icon is inside the link, joined to the number by a word joiner, never a sibling that
        // wraps onto its own line; cl-link-icon (not Bulma's 16 px .icon box) keeps it off the last character
        String documents = html.substring(html.indexOf("id=\"dokumenty\""), html.indexOf("id=\"platnosci\""));
        assertThat(documents).containsPattern("<a href=\"https://faktury.example/118\" target=\"_blank\" rel=\"noopener\">"
                + "<span>FV/2026/09/118</span>(&#8288;|\u2060)<span class=\"cl-link-icon\" aria-hidden=\"true\">"
                + "<i\\s+class=\"fas fa-external-link-alt\"></i></span></a>");
        assertThat(occurrences(documents, "fa-external-link-alt")).isEqualTo(1);
    }

    @Test
    void documentLinkFieldsAcceptOnlyHttpAddresses() {
        // given
        Order order = order(OrderStatus.Delivered);

        // when
        String html = page(render(order, ADMIN));

        // then: type=url alone lets ftp:// or mailto: through; the help says http(s)
        assertThat(html).containsPattern("<input class=\"cl-input\" type=\"url\" id=\"document-link\"[^>]*pattern=\"https\\?://\\.\\+\"");
        assertThat(html).containsPattern("<label class=\"cl-label\" for=\"document-link\">\\s*<span>Link do dokumentu</span>\\s*"
                + "<span class=\"cl-optional\">opcjonalne</span>");
    }

    @Test
    void rowTitlesHaveNoDoubleSpaceBeforeTheSeparator() {
        // given: the title line is a flex row with its own gap, so a leading space doubles it
        Order order = order(OrderStatus.Shipping);
        order.getShipments().get(0).setCarrier("InPost");
        order.getShipments().get(0).setTrackingNo("E2E1");
        order.addPayment(new Payment("REF-1", "Jan Kowalski", PaymentSource.BankTransfer, 500, 0));

        // when
        String html = page(render(order, ADMIN));

        // then
        assertThat(html).contains("<span>· InPost</span>").contains("<span>· Przelew bankowy</span>")
                .doesNotContain("<span> · InPost</span>").doesNotContain("<span> · Przelew");
    }

    @Test
    void aShipmentLineStartsWithACapitalAndAPickupPointIsNotNamedTwice() {
        // given: a courier shipment waiting for its data and a pickup-point one (titled "Punkt odbioru" already)
        Order order = order(OrderStatus.Assembly);
        Shipment pickup = new Shipment(ShipmentType.PickupPoint);
        pickup.setCollectionPointCode("WAW01M");
        order.addShipment(pickup);

        // when
        String html = page(render(order, ADMIN));

        // then: the first part of a line takes a capital, a part after " · " stays lower case
        String shipments = html.substring(html.indexOf("id=\"przesylki\""), html.indexOf("id=\"dokumenty\""));
        assertThat(shipments.replaceAll("\\s+", " ")).contains("<span>Czeka na nadanie</span>")
                .contains("<span>WAW01M</span><span> · </span> <span>czeka na nadanie</span>")
                .doesNotContain("unkt odbioru WAW01M");
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

    @Test
    void theSupplierDialogAsksForNoEanUpFrontAndNamesTheCounterpartyField() {
        // when
        String html = page(render(order(OrderStatus.New), ADMIN));

        // then
        assertThat(html).contains("id=\"assign-supplier-dialog\"").doesNotContain("name=\"ean\"")
                .contains(">Skrót kontrahenta w systemie fakturowym<").doesNotContain(">np. HURT-ABC<");
    }

    @Test
    void theSupplierDialogOffersTheEanFieldOnceTheTaxonomyMissedTheCode() {
        // given
        AssignSupplierForm form = AssignSupplierForm.of("i1", "MFN-X", "100", "net", SupplierChoice.CUSTOM, "HURT-ABC");
        form.setEan("5901234567890");
        Map<String, Object> variables = new HashMap<>();
        variables.put("orderId", "o1");
        variables.put("supplierForm", form);
        variables.put("supplierError", "Tego kodu producenta nie ma w bazie produktów");
        variables.put("suppliers", List.of());
        variables.put("supplierEanField", true);

        // when
        String html = SettingsTemplateRenderer.render(
                "<div th:replace=\"~{orders/details/item-dialogs :: supplierForm}\"></div>", variables);

        // then: the typed value survives the refusal
        assertThat(html).contains("id=\"assign-supplier-ean\"").contains("name=\"ean\"")
                .contains("value=\"5901234567890\"").contains("data-cl-ean-field");
    }

    @Test
    void aRefusedSupplierDialogKeepsTheChosenConnection() {
        // given: the hidden counterparty field is always posted, so a connection arrives with an empty custom name
        AssignSupplierForm form = AssignSupplierForm.of("i1", "MFN-X", "100", "net", "Acme", "");
        Map<String, Object> variables = new HashMap<>();
        variables.put("orderId", "o1");
        variables.put("supplierForm", form);
        variables.put("supplierError", "Tego kodu producenta nie ma w bazie produktów");
        variables.put("suppliers", List.of(new SupplierLabelMap.Option("Acme", "Acme")));

        // when
        String html = SettingsTemplateRenderer.render(
                "<div th:replace=\"~{orders/details/item-dialogs :: supplierForm}\"></div>", variables);

        // then
        assertThat(html).contains("<option value=\"Acme\" selected=\"selected\">")
                .doesNotContain("<option value=\"" + SupplierChoice.CUSTOM + "\" selected=\"selected\">");
    }

    @Test
    void aRefusedSupplierDialogKeepsATypedSupplierName() {
        // given
        AssignSupplierForm form = AssignSupplierForm.of("i1", "MFN-X", "100", "net", SupplierChoice.CUSTOM, "HURT-ABC");
        Map<String, Object> variables = new HashMap<>();
        variables.put("orderId", "o1");
        variables.put("supplierForm", form);
        variables.put("supplierError", "Tego kodu producenta nie ma w bazie produktów");
        variables.put("suppliers", List.of(new SupplierLabelMap.Option("Acme", "Acme")));

        // when
        String html = SettingsTemplateRenderer.render(
                "<div th:replace=\"~{orders/details/item-dialogs :: supplierForm}\"></div>", variables);

        // then
        assertThat(html).contains("<option value=\"" + SupplierChoice.CUSTOM + "\" selected=\"selected\">")
                .contains("value=\"HURT-ABC\"").doesNotContain("<option value=\"Acme\" selected=\"selected\">");
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
                type, true, false, null,
                null, null, true, OrderLabels.Option.of(FulfilmentType.values(), OrderLabels::fulfilmentType));
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
    void aRefundRowShowsWhatThePaidTotalCountsWithOneMinus() {
        // given: a refund saved positive by older code counts as money that came in, so its row shows no minus
        Order typedNegative = order(OrderStatus.Realization);
        typedNegative.addPayment(refund(-100));
        Order storedPositive = order(OrderStatus.Realization);
        storedPositive.addPayment(refund(100));

        // when
        String negative = card(page(render(typedNegative, ADMIN)), "platnosci");
        String positive = card(page(render(storedPositive, ADMIN)), "platnosci");

        // then
        String negativeList = negative.substring(negative.indexOf("<ul class=\"cl-list\""), negative.indexOf("</ul>"));
        assertThat(negativeList).doesNotContain("−−").contains("−100,00 PLN").contains(">Zwrot<");
        assertThat(occurrences(negativeList, "−")).as(negativeList).isEqualTo(1);
        String positiveList = positive.substring(positive.indexOf("<ul class=\"cl-list\""), positive.indexOf("</ul>"));
        assertThat(positiveList).contains(">100,00 PLN<").contains(">Zwrot<").doesNotContain("−");
    }

    @Test
    void aRefundDialogAsksForTheRefundAmountWithoutASign() {
        // given
        Order order = order(OrderStatus.Realization);
        order.addPayment(refund(-100));

        // when
        String dialog = dialog(page(render(order, ADMIN)), "payment-dialog-0");

        // then
        assertThat(dialog).contains(">Kwota zwrotu<").containsPattern("id=\"payment-0-amount\" name=\"amount\"\\s+value=\"100.00\"");
    }

    @Test
    void theMethodIsReadOnlyWithTheReasonOnceTheOrderHasAReceiptAndRemovingStaysOffered() {
        // given
        Order order = order(OrderStatus.Realization);
        order.addDocument(new pl.commercelink.documents.Document("d-1", "PAR/1", null,
                pl.commercelink.documents.DocumentType.Receipt));
        order.addPayment(Payment.bankTransfer("REF-1", "Jan", 100));
        order.addPayment(Payment.bankTransfer("REF-2", "Jan", 50));

        // when
        String html = page(render(order, ADMIN));
        String dialog = dialog(html, "payment-dialog-0");

        // then
        String select = dialog.substring(dialog.indexOf("<select"), dialog.indexOf(">", dialog.indexOf("<select")) + 1);
        assertThat(select).contains("id=\"payment-0-source\"").contains("name=\"source\"")
                .contains("aria-describedby=\"payment-0-source-locked\"").contains("disabled=\"disabled\"");
        assertThat(dialog)
                .contains("<input type=\"hidden\" name=\"source\" value=\"BankTransfer\">")
                .contains("id=\"payment-0-source-locked\">Zamówienie ma już fakturę albo paragon — metody płatności nie zmienisz.<");
        assertThat(card(html, "platnosci")).contains("aria-label=\"Usuń płatność 1\"").contains("/payments/0/remove?version=");
    }

    @Test
    void theDialogOfARefundStoredPositiveWarnsThatPaidGoesDownByTwiceItsAmount() {
        // given
        Order order = order(OrderStatus.Realization);
        order.addPayment(refund(40));
        order.addPayment(refund(-10));

        // when
        String html = page(render(order, ADMIN));

        // then
        assertThat(dialog(html, "payment-dialog-0")).contains("class=\"cl-alert is-warn\"")
                .contains("Ten zwrot był zapisany bez minusa i liczył się jako wpłata. Po zapisaniu „Wpłacono” zmniejszy się o 80,00 PLN.");
        assertThat(dialog(html, "payment-dialog-1")).doesNotContain("bez minusa");
    }

    @Test
    void anOverpaidOrderSaysOverpaymentInTheFinancesCard() {
        // given
        Order order = order(OrderStatus.Realization);
        order.setTotalPrice(199);
        order.addPayment(Payment.bankTransfer("REF-1", "Jan", 1000));

        // when
        String html = page(render(order, ADMIN));

        // then
        String finances = html.substring(html.indexOf("id=\"finances-title\""), html.indexOf("id=\"finances-costs\""));
        assertThat(finances).contains(">Nadpłata<").contains("801,00 PLN").doesNotContain("−801").doesNotContain(">Do zapłaty<");
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
        assertThat(none).contains("<option value=\"\" selected>— nie jest zbierana —</option>").contains("Status opinii");
        assertThat(collected).doesNotContain("nie zbieramy")
                .containsPattern("<option value=\"ToBeCollected\"\\s+selected=\"selected\">Do zebrania</option>");
    }

    /** The product cell of a row: from its th to the price cell. */
    static String productCell(String html, String name) {
        int start = html.lastIndexOf("<th scope=\"row\" class=\"cl-table-key\"", html.indexOf(">" + name + "<"));
        return html.substring(start, html.indexOf("</th>", start));
    }

    @Test
    void consolidationIsACheckInItsOwnColumnNotAMarkerBesideTheName() {
        // given
        Order order = order(OrderStatus.New);
        List<OrderItem> items = items(order);
        items.get(0).setConsolidated(true);

        // when
        String html = page(render(order, items, ADMIN, Set.of()));
        String cell = productCell(html, "AMD Ryzen 7 9800X3D");

        // then: the glyph is hidden from screen readers, the hidden word says it; other rows leave the cell empty
        assertThat(cell).doesNotContain("fa-file-alt").doesNotContain("Łącznie na fakturze").doesNotContain("cl-table-marks");
        String row = html.substring(html.indexOf(cell), html.indexOf("</tr>", html.indexOf(cell)));
        assertThat(row).contains("<td class=\"cl-table-flag\" data-label=\"Połącz na\u00a0FV\"><span class=\"cl-table-flag-mark\" aria-hidden=\"true\">✓</span><span class=\"cl-visually-hidden\">Tak</span></td>");
        assertThat(occurrences(html, "cl-table-flag-mark")).isEqualTo(1);
        assertThat(html).contains("<td class=\"cl-table-flag is-empty\" data-label=\"Połącz na\u00a0FV\"></td>");
    }

    @Test
    void theConsolidationColumnSitsAfterTheStateAndBeforeTheMenuAndTheGroupRowSpansIt() {
        // when
        String admin = page(render(order(OrderStatus.New), ADMIN));
        String closed = page(render(order(OrderStatus.Completed), ADMIN));

        // then
        assertThat(admin).containsPattern("<col class=\"cl-col-state\">\\s*<col class=\"cl-col-flag\">\\s*<col class=\"cl-col-menu\">")
                .containsPattern(">Stan</th>\\s*<th scope=\"col\" class=\"cl-table-flag\">Połącz na\u00a0FV</th>\\s*<th scope=\"col\" class=\"cl-table-actions\">")
                .contains("<th scope=\"rowgroup\" colspan=\"6\">");
        assertThat(closed).containsPattern("<col class=\"cl-col-state\">\\s*<col class=\"cl-col-flag\">\\s*</colgroup>")
                .contains("<th scope=\"rowgroup\" colspan=\"4\">");
    }

    @Test
    void theCommentIsAnAccentToggleWhosePopoverKeepsItsLinesAndHasNoHeadingElement() {
        // given
        Order order = order(OrderStatus.New);
        List<OrderItem> items = items(order);
        items.get(0).setComment("Check the box.\nShip with the SSD.");

        // when
        String cell = productCell(page(render(order, items, ADMIN, Set.of())), "AMD Ryzen 7 9800X3D");

        // then: the comment is no longer a plain line under the codes
        assertThat(cell).contains("<details class=\"cl-note is-accent\">")
                .contains("<summary class=\"cl-note-toggle\" aria-label=\"Komentarz\">")
                .contains("<i class=\"far fa-comment-alt\"></i>")
                .contains("<p class=\"cl-note-title\">Komentarz</p><p class=\"cl-note-text is-pre\">Check the box.\nShip with the SSD.</p>")
                .doesNotContainPattern("<h[1-6]");
        assertThat(cell.indexOf("cl-note is-accent")).isLessThan(cell.indexOf("cl-table-sub"));
        assertThat(cell.substring(cell.indexOf("cl-table-sub"))).doesNotContain("Check the box.");
    }

    @Test
    void theConditionPillSitsOnTheNamesLineBeforeTheCommentToggle() {
        // given
        Order order = order(OrderStatus.New);
        List<OrderItem> items = items(order);
        items.get(0).setCondition(ItemCondition.OpenBox);
        items.get(0).setConsolidated(true);
        items.get(0).setComment("Note");

        // when
        String cell = productCell(page(render(order, items, ADMIN, Set.of())), "AMD Ryzen 7 9800X3D");

        // then
        String marks = cell.substring(cell.indexOf("<span class=\"cl-table-marks\">"), cell.indexOf("<span class=\"cl-table-sub\">"));
        assertThat(marks).containsPattern("<span class=\"cl-status is-warn\">[^<]+</span>");
        assertThat(marks.indexOf("cl-status")).isPositive().isLessThan(marks.indexOf("<details class=\"cl-note is-accent\">"));
        assertThat(marks).doesNotContain("<details class=\"cl-note\">");
        assertThat(cell).doesNotContain("cl-table-pills");
    }

    @Test
    void everyCodeIsLabelledAndTheCodeItselfCopies() {
        // given
        Order order = order(OrderStatus.New);
        OrderItem item = new OrderItem(order.getOrderId(), "Akcesoria", "Kabel", 1, 20, "ACME-KAB-1", false, 0);
        item.setStatus(FulfilmentStatus.New);
        item.setManufacturerCode("SIM-OK");
        item.setSerialNo("23213123");

        // when
        String cell = productCell(page(render(order, List.of(item), ADMIN, Set.of())), "Kabel");

        // then: "MFN", "SKU" and "SN" each stay glued to their code, one dot between the parts
        assertThat(cell).contains("<span>MFN</span>&nbsp;<button type=\"button\" class=\"cl-copy-inline\" data-cl-copy=\"SIM-OK\"")
                .contains("<span>SKU</span>&nbsp;<button type=\"button\" class=\"cl-copy-inline\" data-cl-copy=\"ACME-KAB-1\"")
                .contains("<span>SN</span>&nbsp;<a class=\"cl-table-link\" href=\"/dashboard/item/history?serialNo=23213123\">23213123</a>&#8288;"
                        + "<button type=\"button\" class=\"cl-copy-inline is-icon\" data-cl-copy=\"23213123\" aria-label=\"Kopiuj numer seryjny 23213123\">");
        assertThat(occurrences(cell, "<span class=\"cl-table-sep\">·</span>")).isEqualTo(3);
        assertThat(cell).doesNotContain("class=\"icon is-small\" aria-hidden=\"true\"><i class=\"far fa-copy\">");
    }

    @Test
    void everyCodeCarriesItsOwnCopyIconAndIsOneButton() {
        // given
        Order order = order(OrderStatus.New);
        OrderItem item = new OrderItem(order.getOrderId(), "Akcesoria", "Kabel", 1, 20, "ACME-KAB-1", false, 0);
        item.setStatus(FulfilmentStatus.New);
        item.setManufacturerCode("SIM-OK");
        item.setSerialNo("23213123");

        // when
        String cell = productCell(page(render(order, List.of(item), ADMIN, Set.of())), "Kabel");

        // then: the code and its icon sit in one button (one focus stop), the icon glued to the code's end by a word
        // joiner; the SN link is followed by its own icon-only button; nothing is pinned or hidden any more
        String icon = "<span class=\"cl-copy-icon\" aria-hidden=\"true\">&#8288;<i class=\"far fa-copy\"></i></span>";
        assertThat(cell).contains("aria-label=\"Kopiuj kod producenta SIM-OK\"><span class=\"cl-copy-code\"><span>SIM-OK</span>" + icon + "</span></button>")
                .contains("aria-label=\"Kopiuj SKU ACME-KAB-1\"><span class=\"cl-copy-code\"><span>ACME-KAB-1</span>" + icon + "</span></button>")
                .contains("aria-label=\"Kopiuj numer seryjny 23213123\"><span class=\"cl-copy-icon\" aria-hidden=\"true\"><i class=\"far fa-copy\"></i></span></button>");
        assertThat(occurrences(cell, "<button type=\"button\" class=\"cl-copy-inline")).isEqualTo(3);
        assertThat(occurrences(cell, "fa-copy")).isEqualTo(3);
        assertThat(cell).doesNotContain("is-pinned").doesNotContain("cl-copy-pair").doesNotContain("style=");
    }

    @Test
    void theHeaderCopyButtonsAreIconButtonsLikeTheCodes() {
        // when
        String html = page(render(order(OrderStatus.New), ADMIN));

        // then
        assertThat(html).doesNotContain("is-pinned")
                .containsPattern("<button type=\"button\" class=\"cl-copy-inline is-icon\"\\s+data-cl-copy=\"[^\"]+\" aria-label=\"[^\"]+\" title=\"[^\"]+\">\\s+<span class=\"cl-copy-icon\"><span class=\"icon is-small\" aria-hidden=\"true\"><i class=\"far fa-copy\"></i></span></span>");
    }

    @Test
    void withoutAHistoryLinkTheSerialNumberItselfCopiesAndNoLeadingDotAppears() {
        // given
        Order completed = order(OrderStatus.Completed);
        OrderItem item = new OrderItem(completed.getOrderId(), null, "Kabel", 1, 20, "ACME-KAB-1", false, 0);
        item.setStatus(FulfilmentStatus.Delivered);
        item.setSerialNo("SN-9");
        item.setComment("Read-only note");

        // when
        String cell = productCell(page(render(completed, List.of(item), SUPER_ADMIN, Set.of())), "Kabel");

        // then: a super admin has no history page, the popovers are information and stay
        assertThat(cell).contains("<span class=\"cl-table-sub\">")
                .contains("<span>SN</span>&nbsp;<button type=\"button\" class=\"cl-copy-inline\" data-cl-copy=\"SN-9\"")
                .doesNotContain("cl-table-sep").doesNotContain("/dashboard/item/history")
                .contains("<details class=\"cl-note is-accent\">");
    }

    @Test
    void anItemWithoutCategoryAndCodesRendersNoEmptyCodesLine() {
        // given
        Order order = order(OrderStatus.New);
        OrderItem bare = new OrderItem(order.getOrderId(), null, "Montaż", 1, 99, null, false, 0);
        bare.setStatus(FulfilmentStatus.New);

        // when
        String cell = productCell(page(render(order, List.of(bare), ADMIN, Set.of())), "Montaż");

        // then
        assertThat(cell).doesNotContain("cl-table-sub").doesNotContain("cl-table-marks");
    }

    @Test
    void theSkuAndSerialPrefixesComeFromTheBundles() throws Exception {
        // given
        String template = java.nio.file.Files.readString(
                java.nio.file.Path.of("src/main/resources/templates/orders/details/items.html"));

        // when
        String html = page(render(order(OrderStatus.New), ADMIN));

        // then
        assertThat(template).doesNotContain("· SKU").doesNotContain("· SN").doesNotContain("· MFN")
                .contains("#{order.items.sku.prefix}").contains("#{order.items.sn.prefix}")
                .contains("#{order.items.mfn.prefix}");
        assertThat(html).contains(">Stan</th>").doesNotContain(">Realizacja</th>");
    }

    @Test
    void theReasonABulkActionIsUnavailableIsVisibleTextUnderItsMenuEntryAndNextToRemove() {
        // given
        Order order = order(OrderStatus.Assembly);
        OrderItem dropship = inDelivery(order, "delivery-9", FulfilmentStatus.Ordered);
        String reason = ResourceBundle.getBundle("messages", PL).getString("order.items.action.dropship.locked");

        // when
        String html = page(render(order, List.of(dropship), ADMIN, Set.of(dropship.getItemId())));
        String row = html.substring(html.indexOf("data-cl-selection-bar"), html.indexOf("</form>"));
        row = row.substring(0, row.indexOf("<table"));

        // then: every greyed entry names its reason as text inside it, as the row menu does; "Remove" points to a short
        // form of the same reason right before it, which fits the row's one line
        String shortReason = ResourceBundle.getBundle("messages", PL).getString("order.items.action.dropship.locked.short");
        assertThat(row).containsPattern("data-cl-bulk-action=\"[^\"]*moveSelectedItemsToTheWarehouse\"[^>]*aria-disabled=\"true\"[^>]*>"
                        + "\\s*<span data-cl-bulk-label>Do magazynu</span>\\s*<span class=\"cl-menu-reason\" data-cl-bulk-reason>"
                        + Pattern.quote(reason) + "</span>")
                .containsPattern("<span class=\"cl-help cl-selection-reason\" id=\"bulk-remove-reason\">"
                        + Pattern.quote(shortReason) + "</span>\\s*<button[^>]*class=\"cl-link-button is-danger cl-selection-remove\"")
                .containsPattern("data-cl-bulk-action=\"[^\"]*removeSelectedItemsFromOrder\"[^>]*aria-describedby=\"bulk-remove-reason\"")
                .doesNotContainPattern("\\stitle=").doesNotContain("cl-visually-hidden").doesNotContain("cl-help is-note");
        // the two warehouse entries
        assertThat(occurrences(row, reason)).isEqualTo(2);
    }

    @Test
    void anAvailableActionCarriesAHiddenEmptyReasonTheScriptFillsWhenNoCheckedItemFits() {
        // when
        String html = page(render(order(OrderStatus.New), ADMIN));

        // then
        assertThat(html).containsPattern("data-cl-bulk-action=\"[^\"]*moveSelectedItemsToTheWarehouse\"[^>]*"
                        + "data-skipped=\"Pominięte pozycje nie są zamówione ani przyjęte na stan.\"")
                .containsPattern("<span class=\"cl-menu-reason\" data-cl-bulk-reason hidden=\"hidden\"></span>")
                .contains("<span class=\"cl-help cl-selection-reason\" id=\"bulk-remove-reason\" hidden=\"hidden\"></span>")
                .containsPattern("removeSelectedItemsFromOrder\"[^>]*data-skipped=\"Tylko nowe i usługi\"")
                // the hidden reason describes nothing; order-items.js links it while it shows
                .doesNotContainPattern("removeSelectedItemsFromOrder\"[^>]*aria-describedby")
                .doesNotContainPattern("data-cl-bulk-action=\"[^\"]*moveSelectedItemsToTheWarehouse\"[^>]*aria-disabled");
    }

    @Test
    void theSelectionRowStandsInForTheHeaderWithItsOwnSelectAllTheCountTheMoveMenuAndRemove() {
        // when
        String html = page(render(order(OrderStatus.New), ADMIN));
        String row = html.substring(html.indexOf("<div class=\"cl-selection-row\""), html.indexOf("<table"));

        // then: left to right, which is also the tab order — select-all, "k of n", "Move", "Remove"; the
        // select-all box itself clears the selection, so there is no separate "Clear"
        assertThat(html).contains("<div class=\"cl-selection-row\" data-cl-selection-bar hidden>");
        assertThat(row).containsPattern("<label class=\"cl-check-target\"><input class=\"cl-check-input\" type=\"checkbox\" "
                        + "data-cl-select-all\\s+aria-label=\"Zaznacz wszystkie\" data-cl-label-select=\"Zaznacz wszystkie\" "
                        + "data-cl-label-clear=\"Odznacz wszystkie\">")
                .contains("data-template=\"Zaznaczono {k} z {n}\"");
        assertThat(row).doesNotContain("data-cl-select-clear").doesNotContain("cl-selection-clear");
        List<String> sequence = List.of("data-cl-select-all", "data-cl-selection-count",
                "<span>Przenieś</span>", "Do nowego zamówienia", "Do istniejącego…", "Do magazynu", "Do magazynu (RMA)",
                "cl-link-button is-danger cl-selection-remove");
        int at = -1;
        for (String part : sequence) {
            int next = row.indexOf(part, at + 1);
            assertThat(next).as(part).isGreaterThan(at);
            at = next;
        }
        assertThat(occurrences(row, "<details class=\"cl-menu\">")).isEqualTo(1);
        assertThat(occurrences(row, "<summary class=\"cl-button\">")).isEqualTo(1);
        assertThat(row).doesNotContain("Skieruj").doesNotContain("Do alokacji").doesNotContain("moveSelectedItemsToAllocation");
        assertThat(row).containsPattern("<button type=\"button\" class=\"cl-menu-item\"\\s+data-cl-dialog-open=\"move-dialog\"")
                .doesNotContain("disabled=\"disabled\"").doesNotContain("class=\"cl-selection-bar").doesNotContain("is-primary");
    }

    @Test
    void anInvoicedOrderOffersNoRemoveInTheSelectionRow() {
        // given
        Order order = order(OrderStatus.Assembly);
        order.addDocument(new Document("fv", "FV/2026/09/118", null, DocumentType.InvoiceVat));

        // when
        String html = page(render(order, ADMIN));
        String row = html.substring(html.indexOf("<div class=\"cl-selection-row\""), html.indexOf("<table"));

        // then
        assertThat(row).doesNotContain("removeSelectedItemsFromOrder").doesNotContain("bulk-remove-reason")
                .contains("<span>Przenieś</span>").contains("data-cl-select-all");
    }

    @Test
    void theSelectionActionsCarryTheScopeCountTemplate() {
        // when
        String html = page(render(order(OrderStatus.New), ADMIN));

        // then
        assertThat(html).contains("data-cl-scope-count-template=\"{k} z {n}\"");
    }

    @Test
    void theSettingsCardLeavesTheAffiliateIdAndTheGclidToTheDialog() {
        // given
        Order order = order(OrderStatus.New);
        order.setAffiliateId("AFF-17");
        order.setGclid("GCL-42");
        order.setComment("Zadzwonić przed wysyłką");

        // when
        String html = page(render(order, ADMIN));
        String card = card(html, "settings-title");
        String dialog = dialog(html, "settings-dialog");

        // then: the card is dates, notifications and the comment; the tracking ids are edited in the dialog
        assertThat(card).doesNotContain("ID afiliacji").doesNotContain("AFF-17").doesNotContain("GCLID")
                .doesNotContain("GCL-42").doesNotContain("Typ realizacji").contains("Zadzwonić przed wysyłką");
        assertThat(dialog).contains("value=\"AFF-17\"").contains("value=\"GCL-42\"").contains("id=\"fulfilmentType\"");
    }

    @Test
    void thePreferredShippingRowInTheCardIsOrdinaryWithTheChosenDateLabel() {
        // when
        String card = card(page(render(order(OrderStatus.New), ADMIN)), "settings-title");

        // then
        assertThat(card).containsPattern("<div><dt>Termin wybrany przez klienta</dt><dd>")
                .doesNotContain("cl-kv-wide").doesNotContain("Preferowana wysyłka lub odbiór")
                .doesNotContain("Ustawia klient na stronie zamówienia.");
    }

    @Test
    void theSettingsDialogStillExplainsThePreferredDateIsSetByTheCustomerVisiblyNotAsATooltip() {
        // when
        String dialog = dialog(page(render(order(OrderStatus.New), ADMIN)), "settings-dialog");

        // then
        assertThat(dialog).doesNotContainPattern("<label[^>]*title=")
                .contains("Preferowana wysyłka lub odbiór")
                .contains("<p class=\"cl-help\" id=\"preferredShippingAt-help\">Ustawia klient na stronie zamówienia.</p>");
    }

    @Test
    void theEmptyDocumentsTextLeadsAConsumerOrderToAddDocument() {
        // when
        String b2c = card(page(render(order(OrderStatus.Realization), ADMIN)), "dokumenty");
        String b2b = card(page(render(b2bOrder(OrderStatus.Realization), ADMIN)), "dokumenty");

        // then
        assertThat(b2c).contains("Brak dokumentów. Paragon dodasz przyciskiem „Dodaj dokument”.");
        assertThat(b2b).contains("Brak dokumentów. Następny do wystawienia: Faktura VAT — z menu „Wystaw”.");
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
        assertThat(html).contains("Brak przesyłek. Dodaj przesyłkę przyciskiem „Dodaj przesyłkę”.");
    }

    /** The order with {@code count} events, one hour apart, "Zdarzenie 1" the newest; rendered for an admin. */
    static String renderWithEvents(int count) {
        Order order = order(OrderStatus.New);
        List<OrderEvent> events = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            events.add(new OrderEvent(order.getOrderId(), EventType.action, "Zdarzenie " + i,
                    LocalDateTime.of(2026, 9, 28, 12, 0).minusHours(i)));
        }
        OrderPageModel page = factory(Set.of(), false, events).build(order, items(order), ADMIN, PL);
        return renderPage(page, order);
    }

    @Test
    void theHistoryIsOneListWithTheToggleUnderItAndEveryEventInTheMarkup() {
        // given
        String card = card(renderWithEvents(8), "historia");

        // when
        int lists = card.split("<ol ", -1).length - 1;
        int items = card.split("class=\"cl-timeline-item\"", -1).length - 1;

        // then: one list with all eight events, newest first, none hidden in the markup (no-JS shows them all)
        assertThat(lists).isEqualTo(1);
        assertThat(items).isEqualTo(8);
        assertThat(card).doesNotContain("<details").doesNotContain("<li class=\"cl-timeline-item\" hidden");
        assertThat(card.indexOf("Zdarzenie 1<")).isLessThan(card.indexOf("Zdarzenie 8<"));
        assertThat(card).contains("<ol class=\"cl-timeline\" id=\"history-events\" data-cl-timeline-limit=\"3\">");
        // the row sits after the list, hidden until timeline.js shows it; both buttons name the list they control
        int list = card.indexOf("</ol>");
        int toggle = card.indexOf("data-cl-timeline-toggle");
        assertThat(toggle).isGreaterThan(list);
        assertThat(card).contains("<div class=\"cl-timeline-more\" hidden data-cl-timeline-more")
                .contains("data-shown-text=\"Pokazano starsze zdarzenia: 5\"")
                .contains("data-hidden-text=\"Ukryto starsze zdarzenia: 5\"")
                .contains("<button type=\"button\" class=\"cl-link-button cl-timeline-node\" aria-controls=\"history-events\" aria-expanded=\"false\"")
                .contains("data-cl-timeline-toggle=\"expand\"><span class=\"cl-timeline-node-dot\" aria-hidden=\"true\"></span><span>Pokaż 5 wcześniejszych zdarzeń</span></button>")
                .contains("<button type=\"button\" class=\"cl-link-button cl-timeline-less\" aria-controls=\"history-events\" aria-expanded=\"true\"")
                .contains("data-cl-timeline-toggle=\"collapse\" hidden>Zwiń</button>")
                .contains("<p class=\"cl-visually-hidden\" role=\"status\" data-cl-timeline-status></p>");
        assertThat(card).doesNotContain("style=").doesNotContain("is-hidden");
    }

    @Test
    void upToThreeEventsHaveNoToggleAndNoLimit() {
        // given
        String card = card(renderWithEvents(3), "historia");

        // then
        assertThat(card.split("class=\"cl-timeline-item\"", -1).length - 1).isEqualTo(3);
        assertThat(card).contains("<ol class=\"cl-timeline\" id=\"history-events\">")
                .doesNotContain("data-cl-timeline-limit").doesNotContain("data-cl-timeline-toggle")
                .doesNotContain("data-cl-timeline-status");
    }

    @Test
    void theNodeLabelCountsTheHiddenEventsInTheRightPluralForm() {
        // given: 1, 2, 7 and 13 hidden events behind the three visible ones
        String one = card(renderWithEvents(4), "historia");
        String few = card(renderWithEvents(5), "historia");
        String many = card(renderWithEvents(10), "historia");
        String teen = card(renderWithEvents(16), "historia");

        // then
        assertThat(one).contains("<span>Pokaż 1 wcześniejsze zdarzenie</span>");
        assertThat(few).contains("<span>Pokaż 2 wcześniejsze zdarzenia</span>");
        assertThat(many).contains("<span>Pokaż 7 wcześniejszych zdarzeń</span>");
        assertThat(teen).contains("<span>Pokaż 13 wcześniejszych zdarzeń</span>");
    }

    @Test
    void theDetailsPageLoadsTheTimelineScript() {
        // when
        String html = renderWithEvents(6);

        // then
        assertThat(html).contains("/js/timeline.js");
    }

    // --- e-receipt row in the documents card ---------------------------------------------------------------------

    private static final String ORDER_ID = "3e373abc-1111-2222-3333-444455556666";

    private static ReceiptAttempt attempt(int attemptNo,
                                                                   ReceiptAttemptState state) {
        ReceiptAttempt attempt = new ReceiptAttempt();
        attempt.setReceiptKey(ORDER_ID + ":R" + attemptNo);
        attempt.setAttemptNo(attemptNo);
        attempt.setState(state);
        return attempt;
    }

    private static ReceiptOrderView.Row receiptRow(int attemptNo,
            ReceiptAttemptState state, String tone, String url, String number,
            ReceiptPageProblem problem, boolean canCheck, boolean canClose, boolean canResend,
            String outcome) {
        return new ReceiptOrderView.Row(ORDER_ID + ":R" + attemptNo, state,
                "receipts.state." + state.name(), tone, url, problem, canResend ? null : java.time.Instant.parse("2026-09-28T10:00:00Z"),
                null, canCheck, canClose, canResend, attemptNo, number,
                state == ReceiptAttemptState.FISCALISED ? java.time.Instant.parse("2026-09-28T10:00:00Z") : null,
                outcome, false);
    }

    private static String renderWithReceipts(Order order, OrderPageModelFactory.Viewer viewer, ReceiptOrderState receipts) {
        OrderPageModel page = factory(Set.of(), false, List.of(), receipts).build(order, items(order), viewer, PL);
        return page(renderPage(page, order));
    }

    private static ReceiptOrderState pendingAfterABlockedAttempt() {
        return new ReceiptOrderState(
                List.of(attempt(1, ReceiptAttemptState.BLOCKED),
                        attempt(2, ReceiptAttemptState.PENDING)),
                new ReceiptOrderView(List.of(
                        receiptRow(2, ReceiptAttemptState.PENDING, "is-warn", null, null,
                                new ReceiptPageProblem(
                                        "Paragon czeka na drukarkę fiskalną ponad 48 h.",
                                        "Sprawdź, czy drukarka albo moduł Paragony.pl działa.",
                                        "Szczegóły dla Paragony.pl (Fakturownia)",
                                        "Sprawdź fiscal_status paragonu."), true, true, false, null),
                        receiptRow(1, ReceiptAttemptState.BLOCKED, "is-bad", null, null, null,
                                false, false, false, "brak e-maila klienta")), false),
                false, true);
    }

    @Test
    void theEReceiptIsTheFirstRowOfTheDocumentsCardWithItsPillProblemAndEarlierAttempts() {
        // when
        String html = renderWithReceipts(order(OrderStatus.Shipping), ADMIN, pendingAfterABlockedAttempt());

        // then
        String documents = html.substring(html.indexOf("id=\"dokumenty\""), html.indexOf("id=\"platnosci\""));
        assertThat(documents).contains("id=\"e-paragon\"").contains("E-paragon")
                .contains("<span class=\"cl-status is-warn\">Czeka na drukarkę</span>")
                .contains("Paragon czeka na drukarkę fiskalną ponad 48 h.")
                .contains("<details class=\"cl-row-disclosure\">").contains("Wcześniejsze próby (1)")
                .contains("Próba 1").contains("<span class=\"cl-status is-bad\">Zablokowany</span>")
                .contains("brak e-maila klienta")
                .contains("Sprawdź teraz").contains("Zamknij ręcznie")
                .doesNotContain("Wystaw ponownie").doesNotContain("Brak paragonu").doesNotContain("??");
        // the earlier attempt has no actions: the only receiptKey posted is the newest one's
        assertThat(documents).contains("value=\"" + ORDER_ID + ":R2\"").doesNotContain("value=\"" + ORDER_ID + ":R1\"");
    }

    @Test
    void theEReceiptProblemShowsItsCauseInTheWarnToneTheActionAndTheProviderDetailsFolded() {
        // when
        String html = renderWithReceipts(order(OrderStatus.Shipping), ADMIN, pendingAfterABlockedAttempt());

        // then: cause in the warn tone, action as a plain help line, provider hints under a native disclosure
        String row = html.substring(html.indexOf("id=\"e-paragon\""), html.indexOf("Wcześniejsze próby (1)"));
        assertThat(row)
                .contains("<p class=\"cl-list-desc is-warn\" id=\"e-paragon-problem-2\">"
                        + "Paragon czeka na drukarkę fiskalną ponad 48 h.</p>")
                .contains("<p class=\"cl-list-desc\" id=\"e-paragon-action-2\">"
                        + "Sprawdź, czy drukarka albo moduł Paragony.pl działa.</p>")
                .containsPattern("<details class=\"cl-row-disclosure\" id=\"e-paragon-details-2\">\\s*"
                        + "<summary>Szczegóły dla Paragony.pl \\(Fakturownia\\)</summary>\\s*"
                        + "<p class=\"cl-list-desc\">Sprawdź fiscal_status paragonu.</p>\\s*</details>");
        assertThat(occurrences(html, "id=\"e-paragon-problem-2\"")).isEqualTo(1);
        assertThat(occurrences(html, "id=\"e-paragon-details-2\"")).isEqualTo(1);
        // the superseded attempt shows its short outcome only, never a problem of its own
        assertThat(html).doesNotContain("e-paragon-problem-1");
    }

    @Test
    void anEReceiptProblemWithoutProviderDetailsHasNoDisclosure() {
        // given: a failed attempt whose problem has a cause and an action only
        ReceiptOrderState failed = new ReceiptOrderState(
                List.of(attempt(1, ReceiptAttemptState.FAILED)),
                new ReceiptOrderView(List.of(receiptRow(1,
                        ReceiptAttemptState.FAILED, "is-bad", null, null,
                        new ReceiptPageProblem("System Dev Receipts odrzucił paragon: VAT.",
                                "Popraw przyczynę i kliknij „Wystaw ponownie”.", null, null),
                        false, false, false, "VAT")), true),
                true, false);

        // when
        String html = renderWithReceipts(order(OrderStatus.Shipping), ADMIN, failed);

        // then
        assertThat(html).contains("id=\"e-paragon-problem-1\">System Dev Receipts odrzucił paragon: VAT.</p>")
                .contains("id=\"e-paragon-action-1\">Popraw przyczynę i kliknij „Wystaw ponownie”.</p>")
                .doesNotContain("e-paragon-details-1").doesNotContain("Szczegóły dla");
    }

    @Test
    void zamknijRecznieOpensItsOwnDialogWithRequiredNumberAndOptionalLinkOrItsPageWithoutJavascript() {
        // when
        String html = renderWithReceipts(order(OrderStatus.Shipping), ADMIN, pendingAfterABlockedAttempt());

        // then: the trigger names the dialog and links to the page; the dialog is not named "dialog"
        assertThat(html).containsPattern("<a class=\"cl-link-button\" href=\"/dashboard/orders/" + ORDER_ID
                + "/receipts/close\\?receiptKey=" + ORDER_ID + "%3AR2\"\\s+data-cl-dialog-open=\"receipt-close-2\">Zamknij ręcznie</a>");
        assertThat(occurrences(html, "id=\"receipt-close-2\"")).isEqualTo(1);
        String dialog = html.substring(html.indexOf("<dialog class=\"cl-dialog is-form\" id=\"receipt-close-2\""));
        dialog = dialog.substring(0, dialog.indexOf("</dialog>"));
        assertThat(dialog).contains("aria-labelledby=\"receipt-close-2-title\"").contains("id=\"receipt-close-2-title\"")
                .contains("action=\"/dashboard/orders/" + ORDER_ID + "/receipts/close\"")
                .contains("name=\"receiptKey\" value=\"" + ORDER_ID + ":R2\"")
                .containsPattern("<label class=\"cl-label\" for=\"receipt-close-2-number\">Numer e-paragonu</label>")
                .containsPattern("name=\"number\" required autocomplete=\"off\" id=\"receipt-close-2-number\"")
                .contains("for=\"receipt-close-2-link\"").contains("type=\"url\" name=\"link\"")
                .contains("opcjonalne").contains("rozstrzygnąłeś")
                .contains("data-cl-dialog-close").doesNotContain("style=").doesNotContain("onclick=");
    }

    @Test
    void theCloseReceiptPageWithoutJavascriptPostsTheSameFormAndCancelsBackToTheOrder() {
        // given
        Map<String, Object> variables = new HashMap<>();
        variables.put("navigation", null);
        variables.put("shortId", "3e373abc");
        variables.put("close", new pl.commercelink.web.orders.ReceiptCloseForm(ORDER_ID, ORDER_ID + ":R2", 2));

        // when
        String html = page(SettingsTemplateRenderer.render("orders/receipt-close", variables));

        // then
        assertThat(html).contains("action=\"/dashboard/orders/" + ORDER_ID + "/receipts/close\"")
                .contains("id=\"receipt-close-2-number\"").contains("id=\"receipt-close-2-link\"")
                .contains("href=\"/dashboard/orders/" + ORDER_ID + "\"").contains("Zamknij e-paragon ręcznie")
                .doesNotContain("data-cl-dialog-close").doesNotContain("<dialog").doesNotContain("??");
    }

    @Test
    void theEReceiptEmailStateStartsTheLineWithACapitalWhenThereIsNoDate() {
        // given: closed by hand, its document not on the order yet, so the row has no date; the e-mail went out
        Order order = order(OrderStatus.Delivered);
        ReceiptOrderState receipts = new ReceiptOrderState(List.of(attempt(1, ReceiptAttemptState.CLOSED_MANUALLY)),
                new ReceiptOrderView(List.of(receiptRow(1, ReceiptAttemptState.CLOSED_MANUALLY, "is-neutral", null,
                        "PAR/1", null, false, false, false, null)), false), false, true);

        // when
        String html = renderWithReceipts(order, ADMIN, receipts);

        // then
        String row = html.substring(html.indexOf("id=\"e-paragon\""), html.indexOf("id=\"platnosci\""));
        assertThat(row).contains("<span>E-mail wysłany</span>").doesNotContain("<span>e-mail wysłany</span>");
    }

    @Test
    void aFiscalisedEReceiptLinksItsNumberAndIsNeverUnpinned() {
        // given: its order document is on the order and is the row itself
        Order order = order(OrderStatus.Delivered);
        order.addDocument(new Document(ORDER_ID + ":R1", "PAR/7/2026", "https://paragony.example/7", DocumentType.Receipt,
                LocalDate.of(2026, 9, 28)));
        ReceiptOrderState receipts = new ReceiptOrderState(List.of(attempt(1, ReceiptAttemptState.FISCALISED)),
                new ReceiptOrderView(List.of(receiptRow(1,
                        ReceiptAttemptState.FISCALISED, "is-ok", "https://paragony.example/7",
                        "PAR/7/2026", null, false, false, false, null)), false), false, true);

        // when
        String html = renderWithReceipts(order, ADMIN, receipts);

        // then
        String documents = html.substring(html.indexOf("id=\"dokumenty\""), html.indexOf("id=\"platnosci\""));
        assertThat(documents).containsPattern("<a href=\"https://paragony.example/7\" target=\"_blank\" rel=\"noopener\"><span>PAR/7/2026</span>"
                        + "(&#8288;|\u2060)<span\\s+class=\"cl-link-icon\" aria-hidden=\"true\"><i class=\"fas fa-external-link-alt\"></i></span></a>")
                .contains("<span class=\"cl-status is-ok\">Zafiskalizowany</span>")
                .contains("Zafiskalizowano 28.09.2026").contains("e-mail wysłany")
                .doesNotContain("Odepnij").doesNotContain("Paragon PAR").doesNotContain("cl-list-actions");
        assertThat(occurrences(documents, "PAR/7/2026")).isEqualTo(1);
    }

    @Test
    void eParagonIsAConfirmedEntryOfTheIssueMenu() {
        // given
        ReceiptOrderState receipts = new ReceiptOrderState(List.of(),
                new ReceiptOrderView(List.of(), false), true, false);

        // when
        String html = renderWithReceipts(order(OrderStatus.Shipping), ADMIN, receipts);

        // then: without JavaScript the link leads to the confirmation page
        assertThat(html).contains("id=\"issue-menu\"")
                .containsPattern("<a class=\"cl-menu-item\" data-cl-confirm data-cl-confirm-tone=\"primary\"\\s+href=\"/dashboard/orders/"
                        + ORDER_ID + "/receipts/issue\"")
                .contains("data-cl-confirm-title=\"Wystawić e-paragon?\"")
                .contains("data-cl-confirm-action=\"Wystaw e-paragon\"")
                .contains("E-paragon wystawisz z menu „Wystaw”");
    }

    @Test
    void whileTheEReceiptIsBeingIssuedTheLockedActionsShowGreyedWithTheirReason() {
        // given
        ReceiptOrderState receipts = new ReceiptOrderState(List.of(attempt(1, ReceiptAttemptState.ISSUING)),
                new ReceiptOrderView(List.of(receiptRow(1,
                        ReceiptAttemptState.ISSUING, "is-info", null, null, null, true, true,
                        false, null)), false), false, true);

        // when
        String html = renderWithReceipts(order(OrderStatus.New), ADMIN, receipts);

        // then
        assertThat(html).contains("Trwa wystawianie e-paragonu — pozycji nie dodasz.")
                .doesNotContain("Dodawanie pozycji: Trwa")
                .contains("Trwa wystawianie e-paragonu — danych rozliczeniowych nie zmienisz.")
                .containsPattern("<button type=\"button\" class=\"cl-button\" aria-disabled=\"true\"\\s+aria-describedby=\"document-add-reason\">\\s*"
                        + "<span class=\"icon is-small\" aria-hidden=\"true\"><i class=\"fas fa-plus\"></i></span>\\s*<span>Dodaj dokument</span>\\s*</button>")
                .contains("id=\"document-add-reason\"").contains("Trwa wystawianie e-paragonu — dokumentu nie dodasz ręcznie.")
                .contains("Trwa wystawianie e-paragonu — pozycji nie usuniesz")
                .doesNotContain("id=\"document-dialog\"").doesNotContain("??");
    }

    @Test
    void aSuperAdminSeesTheEReceiptRowWithoutActionsOrDialogs() {
        // when
        String html = renderWithReceipts(order(OrderStatus.Shipping), SUPER_ADMIN, pendingAfterABlockedAttempt());

        // then
        assertThat(html).contains("id=\"e-paragon\"").contains("Czeka na drukarkę")
                .doesNotContain("Sprawdź teraz").doesNotContain("Zamknij ręcznie").doesNotContain("receipt-close-2")
                .doesNotContain("/receipts/");
    }

    // --- cancelling against the e-receipt ---

    /** The order with its product returned, so Order#canBeCancelled holds and only the e-receipt decides. */
    private static String renderCancellable(Order order, ReceiptOrderState receipts) {
        List<OrderItem> items = items(order);
        items.get(0).setStatus(FulfilmentStatus.Returned);
        OrderPageModel page = factory(Set.of(), false, List.of(), receipts).build(order, items, ADMIN, PL);
        return page(renderPage(page, order));
    }

    @Test
    void whileAnEReceiptIsBeingIssuedCancelIsGreyedWithItsReason() {
        // given
        ReceiptOrderState issuing = new ReceiptOrderState(
                List.of(attempt(1, ReceiptAttemptState.ISSUING)),
                new ReceiptOrderView(List.of(receiptRow(1,
                        ReceiptAttemptState.ISSUING, "is-info", null, null, null,
                        true, false, false, null)), false), false, true);

        // when
        String html = renderCancellable(order(OrderStatus.Delivered), issuing);

        // then
        assertThat(html).doesNotContain("href=\"/dashboard/orders/" + ORDER_ID + "/cancel\"")
                .containsPattern("<button type=\"button\" class=\"cl-menu-item is-danger\" aria-disabled=\"true\">\\s*"
                        + "<span>Anuluj zamówienie</span>\\s*<span class=\"cl-menu-reason\">Trwa wystawianie e-paragonu — "
                        + "poczekaj na wynik, zanim anulujesz zamówienie.</span>");
    }

    @Test
    void theCancelDialogOfAnOrderWithAFiscalisedEReceiptSaysCancellingDoesNotUndoIt() {
        // given: fiscalised and attached
        Order order = order(OrderStatus.Delivered);
        order.addDocument(new pl.commercelink.documents.Document(ORDER_ID + ":R1", "PAR/1", null,
                pl.commercelink.documents.DocumentType.Receipt));
        ReceiptOrderState fiscalised = new ReceiptOrderState(
                List.of(attempt(1, ReceiptAttemptState.FISCALISED)),
                new ReceiptOrderView(List.of(receiptRow(1,
                        ReceiptAttemptState.FISCALISED, "is-ok", null, "PAR/1", null,
                        false, false, false, null)), false), false, true);

        // when
        String withReceipt = renderCancellable(order, fiscalised);
        String without = renderCancellable(order(OrderStatus.Delivered), ReceiptOrderState.NONE);

        // then
        assertThat(withReceipt).contains("href=\"/dashboard/orders/" + ORDER_ID + "/cancel\"")
                .contains("data-cl-confirm-message=\"Zamówienie przejdzie w status Anulowane, a ceny usług zostaną wyzerowane. "
                        + "Zamówienie ma zafiskalizowany e-paragon — anulowanie go nie cofa. Pieniądze rozlicz osobno: fakturą korygującą albo zwrotem.\"");
        assertThat(without).contains("href=\"/dashboard/orders/" + ORDER_ID + "/cancel\"")
                .contains("data-cl-confirm-message=\"Zamówienie przejdzie w status Anulowane, a ceny usług zostaną wyzerowane.\"")
                .doesNotContain("zafiskalizowany e-paragon");
    }

    @Test
    void aSettledEReceiptRowSaysNothingIsNeeded() {
        // given
        Order order = order(OrderStatus.Delivered);
        order.addDocument(new pl.commercelink.documents.Document("typed", "PAR/KASA/1", null,
                pl.commercelink.documents.DocumentType.Receipt));
        ReceiptOrderState settled = new ReceiptOrderState(
                List.of(attempt(1, ReceiptAttemptState.BLOCKED)),
                new ReceiptOrderView(List.of(new ReceiptOrderView.Row(
                        ORDER_ID + ":R1", ReceiptAttemptState.BLOCKED,
                        "receipts.state.BLOCKED", "is-neutral", null, null, null, null, false, false, false, 1, null,
                        null, "sprzedaż z kasy (POS) bez e-maila klienta", true)), false), false, false);

        // when
        String html = renderWithReceipts(order, ADMIN, settled);

        // then
        assertThat(html).containsPattern("<span class=\"cl-status is-neutral\">Zablokowany</span>")
                .contains("<p class=\"cl-list-desc\" id=\"e-paragon-settled-1\">Nie wystawiono (sprzedaż z kasy (POS) bez e-maila "
                        + "klienta). Nie trzeba nic robić — zamówienie ma już fakturę albo paragon.</p>")
                .doesNotContain("e-paragon-problem-1").doesNotContain("Wystaw ponownie");
    }

    @Test
    void aDeadEReceiptOfACancelledOrderSaysNothingIsNeeded() {
        // given
        Order order = order(OrderStatus.Cancelled);
        ReceiptOrderState settled = new ReceiptOrderState(
                List.of(attempt(1, ReceiptAttemptState.FAILED)),
                new ReceiptOrderView(List.of(new ReceiptOrderView.Row(
                        ORDER_ID + ":R1", ReceiptAttemptState.FAILED,
                        "receipts.state.FAILED", "is-neutral", null, null, null, null, false, false, false, 1, null,
                        null, "zła stawka VAT", true)), false), false, false);

        // when
        String html = renderWithReceipts(order, ADMIN, settled);

        // then
        assertThat(html).containsPattern("<span class=\"cl-status is-neutral\">[^<]+</span>")
                .contains("<p class=\"cl-list-desc\" id=\"e-paragon-settled-1\">Nie wystawiono (zła stawka VAT). "
                        + "Zamówienie jest anulowane — nie trzeba nic robić.</p>")
                .doesNotContain("e-paragon-problem-1").doesNotContain("Wystaw ponownie");
    }

    @Test
    void aPosSaleWithoutTheCustomersEmailGreysReissueAndShowsTheAdviceAsThreeLines() {
        // given: the POS sale's attempt blocked for the missing e-mail (the walk-in buyer has none of their own)
        Order order = order(OrderStatus.Delivered);
        order.setSource(new OrderSource("operator", OrderSourceType.PointOfSale));
        order.getBillingDetails().setEmail(null);
        ReceiptPageProblem advice = ReceiptPageProblem.ofLines(
                "E-paragonu nie wysłano: sprzedaż z kasy (POS) nie ma e-maila klienta.",
                List.of("Kasa wydrukowała paragon?", "Klient chce e-paragon?", "Nie rób obu."), null, null);
        ReceiptOrderState receipts = new ReceiptOrderState(
                List.of(attempt(1, ReceiptAttemptState.BLOCKED)),
                new ReceiptOrderView(List.of(receiptRow(1,
                        ReceiptAttemptState.BLOCKED, "is-bad", null, null, advice,
                        false, false, false, "sprzedaż z kasy (POS) bez e-maila klienta")), true),
                false, false);

        // when
        String html = renderWithReceipts(order, ADMIN, receipts);

        // then: the cause in the colour of the red pill, one paragraph per alternative
        String row = html.substring(html.indexOf("id=\"e-paragon\""));
        row = row.substring(0, row.indexOf("</li>"));
        assertThat(row).contains("<p class=\"cl-list-desc is-bad\" id=\"e-paragon-problem-1\">")
                .contains("<p class=\"cl-list-desc\" id=\"e-paragon-action-1\">Kasa wydrukowała paragon?</p>")
                .contains("<p class=\"cl-list-desc\" id=\"e-paragon-action-1-2\">Klient chce e-paragon?</p>")
                .contains("<p class=\"cl-list-desc\" id=\"e-paragon-action-1-3\">Nie rób obu.</p>");
        // "Wystaw ponownie" greyed, still focusable, with its reason in the row; no confirmation link
        assertThat(row).containsPattern("<button type=\"button\" class=\"cl-link-button\"\\s+aria-disabled=\"true\""
                        + "\\s+aria-describedby=\"e-paragon-reissue-reason-1\">Wystaw ponownie</button>")
                .contains("id=\"e-paragon-reissue-reason-1\">Najpierw wpisz e-mail klienta w danych rozliczeniowych.</p>")
                .doesNotContain("/receipts/reissue");
    }

    @Test
    void reissueIsConfirmedWithThePrimaryButtonAndTheCashRegisterSentenceForAPosSale() {
        // given: the POS sale got the customer's e-mail since its attempt blocked
        Order order = order(OrderStatus.Delivered);
        order.setSource(new OrderSource("operator", OrderSourceType.PointOfSale));
        ReceiptOrderState receipts = new ReceiptOrderState(
                List.of(attempt(1, ReceiptAttemptState.BLOCKED)),
                new ReceiptOrderView(List.of(receiptRow(1,
                        ReceiptAttemptState.BLOCKED, "is-bad", null, null, null,
                        false, false, false, null)), true),
                false, false);

        // when
        String html = renderWithReceipts(order, ADMIN, receipts);

        // then
        assertThat(html).containsPattern("<a class=\"cl-link-button\" data-cl-confirm\\s+data-cl-confirm-tone=\"primary\"")
                .contains("w systemie e-paragonów").contains("Jeśli kasa wydrukowała paragon, nie wystawiaj e-paragonu.")
                .doesNotContain("panelu dostawcy").doesNotContain("e-paragon-reissue-reason-1");
    }

    @Test
    void theEReceiptEntryOfTheIssueMenuIsGreyedForAPosSaleWithoutTheCustomersEmail() {
        // given
        Order order = order(OrderStatus.New);
        order.setSource(new OrderSource("operator", OrderSourceType.PointOfSale));
        order.getBillingDetails().setEmail(null);
        ReceiptOrderState receipts = new ReceiptOrderState(List.of(),
                new ReceiptOrderView(List.of(), false), true, false);

        // when
        String html = renderWithReceipts(order, ADMIN, receipts);

        // then
        String menu = html.substring(html.indexOf("id=\"issue-menu\""));
        menu = menu.substring(0, menu.indexOf("</ul>"));
        assertThat(menu).containsPattern("<button type=\"button\" class=\"cl-menu-item\"\\s+aria-disabled=\"true\">\\s*"
                        + "<span>E-paragon</span>\\s*<span class=\"cl-menu-reason\">Najpierw wpisz e-mail klienta w danych rozliczeniowych.</span>")
                .doesNotContain("/receipts/issue");
    }
}
