package pl.commercelink.web;

import org.junit.jupiter.api.Test;
import pl.commercelink.documents.Document;
import pl.commercelink.documents.DocumentType;
import pl.commercelink.inventory.supplier.SupplierLabelMap;
import pl.commercelink.inventory.supplier.SupplierLabels;
import pl.commercelink.orders.BillingDetails;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.orders.Shipment;
import pl.commercelink.orders.ShipmentType;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.orders.OrderLinks;
import pl.commercelink.web.orders.OrderPrintView;
import pl.commercelink.web.settings.SettingsTemplateRenderer;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** The two order printouts as the controller renders them (Polish messages, real layout). */
class OrderPrintTemplateTest {

    static final String ORDER_ID = "3e373abc-1111-2222-3333-444455556666";

    static Order order() {
        Order order = new Order("store-1");
        order.setOrderId(ORDER_ID);
        order.setOrderedAt(LocalDateTime.of(2026, 9, 27, 21, 33));
        BillingDetails billing = new BillingDetails();
        billing.setEmail("anna.nowak74@test.com");
        order.setBillingDetails(billing);
        order.setComment("Zadzwonić przed wysyłką");
        order.getDocuments().add(new Document("d1", "FV/6/2026", null, DocumentType.InvoiceVat));
        Shipment shipment = new Shipment(ShipmentType.Courier);
        shipment.setCarrier("InPost");
        shipment.setTrackingNo("E2E0000000001");
        shipment.setShippedAt(LocalDateTime.of(2026, 9, 26, 21, 33));
        order.getShipments().add(shipment);
        return order;
    }

    static List<OrderItem> items() {
        OrderItem memory = new OrderItem(ORDER_ID, "Memory", "G.Skill TwinMatch 32GB DDR5 Kit", 1, 499, "SKU", false, 0);
        memory.setManufacturerCode("MFN-TWIN-01");
        memory.setSerialNo("SN-E2E-0006");
        memory.setComment("Sprawdzić etykietę");
        OrderItem service = new OrderItem(ORDER_ID, "Usługi", "Montaż i testy zestawu", 1, 299, "SRV", false, 0);
        service.setService(true);
        return List.of(memory, service);
    }

    static String card(Order order, List<OrderItem> items, boolean superAdmin) {
        OrderPrintView.Card view = OrderPrintView.card(order, items, OrderLinks.of(order, superAdmin),
                noLabels());
        return body(render("orders/card", view));
    }

    static String collection(List<OrderItem> items) {
        Order order = order();
        OrderPrintView.Collection view = OrderPrintView.collection(order, items, store(),
                LocalDate.of(2026, 9, 28), "Kraków, PL", OrderLinks.of(order, false), noLabels());
        return body(render("orders/collection", view));
    }

    static SupplierLabelMap noLabels() {
        return new SupplierLabels(mock(StoresRepository.class)).forStore(null);
    }

    static Store store() {
        Store store = new Store();
        store.setStoreId("store-1");
        store.setName("Demo");
        return store;
    }

    static String render(String template, Object view) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("navigation", null);
        variables.put("print", view);
        return SettingsTemplateRenderer.render(template, variables);
    }

    /** Cuts before the layout tail (</main>), which carries the old delete/save modals (onclick=, class="button"). */
    static String body(String html) {
        int start = html.indexOf("<section class=\"cl-page cl-print-page\"");
        return html.substring(start, html.indexOf("</main>", start));
    }

    static int occurrences(String html, String needle) {
        return html.split(Pattern.quote(needle), -1).length - 1;
    }

    @Test
    void theCardPrintsEveryFieldOfTheOldCard() {
        // when
        String html = card(order(), items(), false);

        // then
        assertThat(occurrences(html, "<h1")).isEqualTo(1);
        assertThat(html).contains("Karta zamówienia")
                .contains("ID zamówienia").contains(ORDER_ID)
                .contains("anna.nowak74@test.com").contains("27.09.2026, 21:33")
                .contains("Memory").contains("G.Skill TwinMatch 32GB DDR5 Kit").contains("MFN-TWIN-01")
                .contains("Sprawdzić etykietę").contains("Montaż i testy zestawu")
                .contains("Zadzwonić przed wysyłką")
                .contains("FV/6/2026").contains("Faktura VAT").contains("Wystawiony")
                .contains("Kurier").contains("E2E0000000001").contains("InPost").contains("26.09.2026, 21:33")
                .doesNotContain("??").doesNotContain("null");
        assertThat(occurrences(html, "<h2")).isEqualTo(4);
    }

    @Test
    void theCardUsesTheNewLayer() {
        // when
        String html = card(order(), items(), false);

        // then
        assertThat(html).contains("class=\"cl-card cl-print-sheet\"").contains("class=\"cl-table is-compact cl-print-table\"")
                .contains("data-label=\"Kod producenta\"").contains("<th scope=\"col\"")
                .doesNotContain("class=\"box").doesNotContain("class=\"table");
    }

    /**
     * The order page prints both sheets from a hidden frame (print.js), so the page is no longer a preview: it carries
     * the sheet and its title only, without the app frame, a way back, a Print button, scripts or analytics.
     */
    @Test
    void bothPrintoutsAreTheBareSheetWithoutThePreviewChrome() {
        // given
        Order order = order();

        // when
        String card = render("orders/card", OrderPrintView.card(order, items(), OrderLinks.of(order, false), noLabels()));
        String collection = render("orders/collection", OrderPrintView.collection(order, items(), store(),
                LocalDate.of(2026, 9, 28), "Kraków, PL", OrderLinks.of(order, false), noLabels()));

        // then
        for (String html : List.of(card, collection)) {
            assertThat(html).contains("<main id=\"clContent\" class=\"cl-content\">")
                    .contains("<div class=\"container content is-fluid\">")
                    .contains("/css/commercelink.css")
                    .doesNotContain("<script").doesNotContain("data-cl-print").doesNotContain("cl-back")
                    .doesNotContain("cl-page-actions").doesNotContain("cl-sidebar").doesNotContain("cl-topbar")
                    .doesNotContain("deleteModal").doesNotContain("googletagmanager");
            assertThat(occurrences(html, "<h1")).isEqualTo(1);
        }
        assertThat(card).contains("<title>Karta zamówienia · Zamówienie " + order.getShortenedOrderId() + "</title>");
        assertThat(collection).contains("<title>Protokół odbioru · Zamówienie " + order.getShortenedOrderId() + "</title>");
    }

    @Test
    void theCardOfASuperAdminLeadsBackToTheStoreScopedOrder() {
        // when
        String html = card(order(), items(), true);

        // then: the old card linked the order id to /dashboard/orders/…, which a super admin cannot open
        assertThat(html).contains("href=\"/dashboard/store/store-1/orders/" + ORDER_ID + "\"")
                .doesNotContain("href=\"/dashboard/orders/");
    }

    @Test
    void theDeliveryColumnIsNamedForWhatItHoldsAndCodesStayWhole() {
        // when
        String html = card(order(), items(), false);

        // then: the value is a supplier's name or a shortened delivery number, not only a delivery number
        assertThat(html).contains("Dostawca / dostawa").doesNotContain("Nr dostawy")
                .contains("<td class=\"cl-print-code\" data-label=\"Kod producenta\">MFN-TWIN-01</td>");
    }

    @Test
    void severalSerialNumbersAreSeparateUnbreakableCodes() {
        // given
        List<OrderItem> items = items();
        items.get(0).setSerialNo("9M4R2K7Q01234,SN-2 ");

        // when
        String html = collection(items);

        // then
        assertThat(html.replaceAll("\\s+", " ")).contains("<span class=\"cl-print-code\">9M4R2K7Q01234</span>, <span class=\"cl-print-code\">SN-2</span>");
    }

    @Test
    void anEmptyCardSaysWhatIsMissingInsteadOfPrintingBareHeaders() {
        // given
        Order order = new Order("store-1");
        order.setOrderId(ORDER_ID);

        // when
        String html = card(order, List.of(), false);

        // then
        assertThat(html).contains("Brak pozycji").contains("Brak komentarza").contains("Brak dokumentów")
                .contains("Brak przesyłek").doesNotContain("null");
    }

    @Test
    void theProtocolPrintsProductsTheStatementAndTwoLabelledSignatureLines() {
        // when
        String html = collection(items());

        // then
        assertThat(occurrences(html, "<h1")).isEqualTo(1);
        assertThat(html).contains("Protokół odbioru").contains("store-1 (Demo)").contains(ORDER_ID)
                .contains("28.09.2026").contains("Kraków, PL")
                .contains("G.Skill TwinMatch 32GB DDR5 Kit").contains("SN-E2E-0006").contains("MFN-TWIN-01")
                .doesNotContain("Montaż i testy zestawu")
                .contains("Potwierdzam odbiór towaru")
                .contains("id=\"signature-store\"").contains("for=\"signature-store\"").contains("Podpis pracownika sklepu")
                .contains("id=\"signature-client\"").contains("for=\"signature-client\"").contains("Podpis klienta")
                .doesNotContain("??").doesNotContain("class=\"input");
    }

    @Test
    void aProtocolOfServicesOnlySaysThereIsNothingToHandOver() {
        // when
        String html = collection(List.of(items().get(1)));

        // then
        assertThat(html).contains("Brak produktów do wydania").doesNotContain("<table");
    }

    @Test
    void theOldBulmaPrintTemplatesAreGone() {
        // then
        assertThat(Path.of("src/main/resources/templates/orderCard.html")).doesNotExist();
        assertThat(Path.of("src/main/resources/templates/orderPersonalCollection.html")).doesNotExist();
    }
}
