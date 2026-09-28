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
import pl.commercelink.orders.event.EventType;
import pl.commercelink.orders.event.OrderEvent;
import pl.commercelink.orders.event.OrderEventsRepository;
import pl.commercelink.orders.fulfilment.FulfilmentType;
import pl.commercelink.products.ProductCatalogRepository;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.taxonomy.TaxonomyCache;
import pl.commercelink.warehouse.api.ItemCondition;
import pl.commercelink.web.orders.OrderPageModel;
import pl.commercelink.web.orders.OrderPageModelFactory;
import pl.commercelink.web.orders.OrderAddressForm;
import pl.commercelink.web.orders.OrderLabels;
import pl.commercelink.web.orders.OrderSettingsView;
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
        when(events.findByOrderId(anyString())).thenReturn(orderEvents);
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
    void theFulfilmentTypeShowsAsAnIconAndAShortLabelInTheHeaderAndTheSettingsCard() {
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

        // then: decorative icon + short text in both read-only places, not the long store-settings label
        for (String html : List.of(warehouseHeader, warehouseSettings)) {
            assertThat(html).contains("cl-icon-text").contains("fas fa-warehouse\" aria-hidden=\"true\"")
                    .contains("Magazyn sklepu").doesNotContain("Przez magazyn sklepu");
        }
        for (String html : List.of(dropshipHeader, dropshipSettings)) {
            assertThat(html).contains("cl-icon-text").contains("fas fa-truck\" aria-hidden=\"true\"")
                    .contains("Dropshipping").doesNotContain("Wysyłką od dostawcy do klienta");
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

        // then: main column first, side column after (dates and settings, customer, finances, history — the client's
        // order); the stylesheet stacks them in this same order below 1366 px and never reorders them
        List<Integer> positions = List.of(html.indexOf("id=\"items-title\""), html.indexOf("id=\"shipments-title\""),
                html.indexOf("id=\"documents-title\""), html.indexOf("id=\"payments-title\""),
                html.indexOf("id=\"settings-title\""), html.indexOf("id=\"customer-title\""),
                html.indexOf("id=\"finances-title\""), html.indexOf("id=\"history-title\""));
        assertThat(positions).doesNotContain(-1).isSorted();
    }

    @Test
    void theItemsTableHasSixColumnsAndServicesInTheirOwnGroup() {
        // when
        String html = page(render(order(OrderStatus.New), ADMIN));

        // then
        assertThat(html).contains("<col class=\"cl-col-check\">").contains("<col class=\"cl-col-flag\">").contains("<col class=\"cl-col-menu\">")
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
        assertThat(user).contains("koszt 579").contains("Zysk (z VAT)").contains("Koszt produktów (brutto)");
        assertThat(admin).contains("koszt 579").contains("Zysk (z VAT)").contains("Koszt produktów (brutto)");
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
                .contains("Po wystawieniu faktury danych rozliczeniowych nie zmienisz.");
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
                .contains("Telefon jest wymagany").doesNotContain("??");
        assertThat(occurrences(html, "class=\"cl-field-error\"")).isEqualTo(8);
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
        assertThat(completed).doesNotContain("Usunąć można tylko nowe zamówienie");
        assertThat(open).contains("Usunąć można tylko nowe zamówienie");
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
        assertThat(html).contains("id=\"finances-costs\"").contains("<summary>Koszt i zysk</summary>").contains("koszt 579")
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
        assertThat(html).doesNotContain("Następny do wystawienia").contains("Brak paragonu/faktury");
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

        // then: the bulk actions are greyed through aria-disabled (menu entries and "Remove" stay focusable with
        // their reason); the add-items button keeps its native disabled
        assertThat(html).containsPattern("data-cl-bulk-action=\"[^\"]*moveSelectedItemsToTheWarehouse\"[^>]*aria-disabled=\"true\"")
                .containsPattern("data-cl-bulk-action=\"[^\"]*moveSelectedItemsToAllocation\"[^>]*aria-disabled=\"true\"")
                // the dropship lock disables every bulk action, not only the two above
                .containsPattern("data-cl-bulk-action=\"[^\"]*moveSelectedItemsToTheWarehouseForRMA\"[^>]*aria-disabled=\"true\"")
                .containsPattern("data-cl-bulk-action=\"[^\"]*removeSelectedItemsFromOrder\"[^>]*aria-disabled=\"true\"")
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
                type, OrderLabels.fulfilmentTypeShort(type), OrderLabels.fulfilmentTypeIcon(type), true, false, null,
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
        assertThat(row).containsPattern("data-cl-bulk-action=\"[^\"]*moveSelectedItemsToAllocation\"[^>]*aria-disabled=\"true\"[^>]*>"
                        + "\\s*<span data-cl-bulk-label>Do alokacji</span>\\s*<span class=\"cl-menu-reason\" data-cl-bulk-reason>"
                        + Pattern.quote(reason) + "</span>")
                .containsPattern("<span class=\"cl-help cl-selection-reason\" id=\"bulk-remove-reason\">"
                        + Pattern.quote(shortReason) + "</span>\\s*<button[^>]*class=\"cl-link-button is-danger cl-selection-remove\"")
                .containsPattern("data-cl-bulk-action=\"[^\"]*removeSelectedItemsFromOrder\"[^>]*aria-describedby=\"bulk-remove-reason\"")
                .doesNotContainPattern("\\stitle=").doesNotContain("cl-visually-hidden").doesNotContain("cl-help is-note");
        // the three routing entries
        assertThat(occurrences(row, reason)).isEqualTo(3);
    }

    @Test
    void anAvailableActionCarriesAHiddenEmptyReasonTheScriptFillsWhenNoCheckedItemFits() {
        // when
        String html = page(render(order(OrderStatus.New), ADMIN));

        // then
        assertThat(html).containsPattern("data-cl-bulk-action=\"[^\"]*moveSelectedItemsToAllocation\"[^>]*"
                        + "data-skipped=\"Pominięte pozycje nie mają kompletu danych alokacji albo nie są nowe.\"")
                .containsPattern("<span class=\"cl-menu-reason\" data-cl-bulk-reason hidden=\"hidden\"></span>")
                .contains("<span class=\"cl-help cl-selection-reason\" id=\"bulk-remove-reason\" hidden=\"hidden\"></span>")
                .containsPattern("removeSelectedItemsFromOrder\"[^>]*data-skipped=\"Tylko nowe i usługi\"")
                // the hidden reason describes nothing; order-items.js links it while it shows
                .doesNotContainPattern("removeSelectedItemsFromOrder\"[^>]*aria-describedby")
                .doesNotContainPattern("data-cl-bulk-action=\"[^\"]*moveSelectedItemsToAllocation\"[^>]*aria-disabled");
    }

    @Test
    void theSelectionRowStandsInForTheHeaderWithItsOwnSelectAllTheCountTwoMenusAndRemove() {
        // when
        String html = page(render(order(OrderStatus.New), ADMIN));
        String row = html.substring(html.indexOf("<div class=\"cl-selection-row\""), html.indexOf("<table"));

        // then: left to right, which is also the tab order — select-all, "k of n", "Route to", "Move", "Remove"; the
        // select-all box itself clears the selection, so there is no separate "Clear"
        assertThat(html).contains("<div class=\"cl-selection-row\" data-cl-selection-bar hidden>");
        assertThat(row).containsPattern("<label class=\"cl-check-target\"><input class=\"cl-check-input\" type=\"checkbox\" "
                        + "data-cl-select-all\\s+aria-label=\"Zaznacz wszystkie\" data-cl-label-select=\"Zaznacz wszystkie\" "
                        + "data-cl-label-clear=\"Odznacz wszystkie\">")
                .contains("data-template=\"Zaznaczono {k} z {n}\"");
        assertThat(row).doesNotContain("data-cl-select-clear").doesNotContain("cl-selection-clear");
        List<String> sequence = List.of("data-cl-select-all", "data-cl-selection-count",
                "<span>Skieruj</span>", "Do alokacji", "Do magazynu", "Do magazynu (RMA)", "<span>Przenieś</span>",
                "Do nowego zamówienia", "Do istniejącego…", "cl-link-button is-danger cl-selection-remove");
        int at = -1;
        for (String part : sequence) {
            int next = row.indexOf(part, at + 1);
            assertThat(next).as(part).isGreaterThan(at);
            at = next;
        }
        assertThat(occurrences(row, "<details class=\"cl-menu\">")).isEqualTo(2);
        assertThat(occurrences(row, "<summary class=\"cl-button\">")).isEqualTo(2);
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
                .contains("<span>Skieruj</span>").contains("data-cl-select-all");
    }

    @Test
    void theSelectionActionsCarryTheScopeCountTemplate() {
        // when
        String html = page(render(order(OrderStatus.New), ADMIN));

        // then
        assertThat(html).contains("data-cl-scope-count-template=\"{k} z {n}\"");
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
        assertThat(card).contains("<ol class=\"cl-timeline\" id=\"history-events\" data-cl-timeline-limit=\"5\">");
        // the row sits after the list, hidden until timeline.js shows it; both buttons name the list they control
        int list = card.indexOf("</ol>");
        int toggle = card.indexOf("data-cl-timeline-toggle");
        assertThat(toggle).isGreaterThan(list);
        assertThat(card).contains("<div class=\"cl-timeline-more\" hidden data-cl-timeline-more")
                .contains("data-shown-text=\"Pokazano starsze zdarzenia: 3\"")
                .contains("data-hidden-text=\"Ukryto starsze zdarzenia: 3\"")
                .contains("<button type=\"button\" class=\"cl-link-button cl-timeline-node\" aria-controls=\"history-events\" aria-expanded=\"false\"")
                .contains("data-cl-timeline-toggle=\"expand\"><span class=\"cl-timeline-node-dot\" aria-hidden=\"true\"></span><span>Pokaż 3 wcześniejsze zdarzenia</span></button>")
                .contains("<button type=\"button\" class=\"cl-link-button cl-timeline-less\" aria-controls=\"history-events\" aria-expanded=\"true\"")
                .contains("data-cl-timeline-toggle=\"collapse\" hidden>Zwiń</button>")
                .contains("<p class=\"cl-visually-hidden\" role=\"status\" data-cl-timeline-status></p>");
        assertThat(card).doesNotContain("style=").doesNotContain("is-hidden");
    }

    @Test
    void upToFiveEventsHaveNoToggleAndNoLimit() {
        // given
        String card = card(renderWithEvents(5), "historia");

        // then
        assertThat(card.split("class=\"cl-timeline-item\"", -1).length - 1).isEqualTo(5);
        assertThat(card).contains("<ol class=\"cl-timeline\" id=\"history-events\">")
                .doesNotContain("data-cl-timeline-limit").doesNotContain("data-cl-timeline-toggle")
                .doesNotContain("data-cl-timeline-status");
    }

    @Test
    void theNodeLabelCountsTheHiddenEventsInTheRightPluralForm() {
        // given: 1, 5 and 13 hidden events behind the five visible ones
        String one = card(renderWithEvents(6), "historia");
        String many = card(renderWithEvents(10), "historia");
        String teen = card(renderWithEvents(18), "historia");

        // then
        assertThat(one).contains("<span>Pokaż 1 wcześniejsze zdarzenie</span>");
        assertThat(many).contains("<span>Pokaż 5 wcześniejszych zdarzeń</span>");
        assertThat(teen).contains("<span>Pokaż 13 wcześniejszych zdarzeń</span>");
    }

    @Test
    void theDetailsPageLoadsTheTimelineScript() {
        // when
        String html = renderWithEvents(6);

        // then
        assertThat(html).contains("/js/timeline.js");
    }
}
