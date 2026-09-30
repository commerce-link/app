package pl.commercelink.web;

import org.junit.jupiter.api.Test;
import pl.commercelink.inventory.deliveries.DeliveryRedirectResolver;
import pl.commercelink.inventory.supplier.SupplierLabelMap;
import pl.commercelink.inventory.supplier.SupplierLabels;
import pl.commercelink.orders.FulfilmentStatus;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.orders.OrderStatus;
import pl.commercelink.products.StoreCategories;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.stores.ConnectionMode;
import pl.commercelink.stores.FulfilmentConfiguration;
import pl.commercelink.stores.StoreSupplierConnection;
import pl.commercelink.web.orders.ItemSaleLock;
import pl.commercelink.web.orders.OrderItemRow;
import pl.commercelink.web.orders.OrderLabels;
import pl.commercelink.web.orders.OrderLinks;
import pl.commercelink.web.orders.OrderPageModelFactory;
import pl.commercelink.web.settings.SettingsTemplateRenderer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** The item page (orders/item) as OrdersController#getOrderItem fills it: form, locks and the read-only closed view. */
class OrderItemPageTemplateTest {

    static final String ORDER_ID = "3e373abc-1111-2222-3333-444455556666";
    static final String DELIVERY_ID = "b58e2f14-0001-4c7a-8b21-demo00000002";

    static OrderItem item(FulfilmentStatus status) {
        OrderItem item = new OrderItem(ORDER_ID, "Laptopy", "Laptop Pro 14", 2, 4999.0, "SKU-1", false);
        item.setItemId("item-7");
        item.setStatus(status);
        item.setEan("5901234123457");
        item.setManufacturerCode("LP14-2026");
        item.setCost(3500.0);
        item.setSerialNo("SN-1, SN-2");
        item.setComment("Klient prosi o fakturę na firmę");
        return item;
    }

    static Map<String, Object> variables(OrderItem item, boolean closed) {
        SupplierLabelMap labels = new SupplierLabels(mock(StoresRepository.class)).forStore(store());
        Map<String, Object> variables = new HashMap<>();
        variables.put("navigation", null);
        variables.put("orderId", ORDER_ID);
        variables.put("shortId", "3e373abc");
        variables.put("orderItem", item);
        variables.put("categories", List.of("Laptopy", "Monitory"));
        variables.put("categoryGroups", List.of(new StoreCategories.Group("Sklep", List.of("Laptopy", "Monitory"))));
        variables.put("isCompletedOrder", closed);
        variables.put("serviceFlagLocked", false);
        variables.put("priceLocked", false);
        variables.put("consolidationLocked", false);
        variables.put("serialHistory", OrderLinks.serialHistory(item.getSerialNo()));
        variables.put("statusKey", OrderLabels.itemStatus(item.getStatus()));
        variables.put("statusTone", OrderLabels.tone(item.getStatus()));
        variables.put("suppliers", labels.options());
        DeliveryRedirectResolver resolver = new DeliveryRedirectResolver();
        boolean held = resolver.pointsToDelivery(item);
        variables.put("delivery", item.getDeliveryId() == null ? null
                : new OrderItemRow.Delivery(OrderItemRow.deliveryLabel(item, labels), resolver.resolveFor(item), held));
        variables.put("deliveryHeld", held);
        variables.put("supplierCurrent", labels.has(item.getDeliveryId()) ? item.getDeliveryId() : null);
        variables.put("supplierCustom", null);
        return variables;
    }

    static Store store() {
        StoreSupplierConnection connection = new StoreSupplierConnection("Acme", ConnectionMode.OWN);
        FulfilmentConfiguration config = new FulfilmentConfiguration();
        config.setSupplierConnections(new ArrayList<>(List.of(connection)));
        Store store = new Store();
        store.setStoreId("store-1");
        store.setFulfilmentConfiguration(config);
        return store;
    }

    static String render(Map<String, Object> variables) {
        String html = SettingsTemplateRenderer.render("orders/item", variables);
        int start = html.indexOf("<section class=\"cl-page\"");
        return html.substring(start, html.indexOf("</main>", start));
    }

    @Test
    void theItemPageLinksTheHistoryOfEachOfItsSerialNumbers() {
        // given
        OrderItem single = item(FulfilmentStatus.Delivered);
        single.setSerialNo("SN-1");
        OrderItem none = item(FulfilmentStatus.Delivered);
        none.setSerialNo(null);

        // when
        String several = render(variables(item(FulfilmentStatus.Delivered), false));
        String one = render(variables(single, true));
        String without = render(variables(none, false));

        // then: one serial number is one link; several are a menu of their own links, in the page header's actions
        assertThat(one).containsPattern("<div class=\"cl-page-actions\">\\s*<a class=\"cl-button\" id=\"item-history-link\"\\s+"
                + "href=\"/dashboard/item/history\\?serialNo=SN-1\">[\\s\\S]*?<span>Historia przedmiotu</span>");
        assertThat(several).contains("<details class=\"cl-menu\" id=\"item-history-menu\">")
                .contains("<a class=\"cl-menu-item\" href=\"/dashboard/item/history?serialNo=SN-1\">SN SN-1</a>")
                .contains("<a class=\"cl-menu-item\" href=\"/dashboard/item/history?serialNo=SN-2\">SN SN-2</a>");
        assertThat(without).doesNotContain("/dashboard/item/history").doesNotContain("cl-page-actions");
    }

    @Test
    void aNewItemIsEditedInOneFormWithTheOldFieldNamesAndTheDialogsSupplierChoice() {
        // when
        String html = render(variables(item(FulfilmentStatus.New), false));

        // then
        assertThat(html).contains("action=\"/dashboard/orders/" + ORDER_ID + "/items/item-7/save\"")
                .contains("href=\"/dashboard/orders/" + ORDER_ID + "\"").contains("Zamówienie 3e373abc")
                .containsPattern("<h1 class=\"cl-page-title\">Laptop Pro 14</h1>")
                .contains("Produkt").contains("Cena i ilość").contains("Realizacja");
        for (String name : List.of("name", "category", "sku", "ean", "manufacturerCode", "qty", "price", "cost", "tax",
                "serialNo", "comment", "service", "consolidated", "deliveryId", "customSupplier")) {
            assertThat(html).as(name).contains("name=\"" + name + "\"");
        }
        assertThat(html).contains("value=\"Laptop Pro 14\"").contains("value=\"4999.00\"").contains("value=\"3500.00\"")
                .contains("value=\"1.23\"").contains("<option value=\"Laptopy\" selected=\"selected\">Laptopy</option>")
                .contains("<option value=\"Acme\">Acme</option>").contains("Bez dostawcy")
                .doesNotContain("disabled").doesNotContain("id=\"item-locked-note\"").doesNotContain("name=\"supplier\"")
                .doesNotContain("name=\"status\"").contains("Zapisz pozycję")
                .doesNotContain("class=\"input").doesNotContain("class=\"box").doesNotContain("??");
    }

    @Test
    void everyControlHasALabel() {
        // when
        String html = render(variables(item(FulfilmentStatus.New), false));

        // then
        for (String id : List.of("item-name", "item-category", "item-sku", "item-ean", "item-mfn", "item-qty", "item-price",
                "item-cost", "item-tax", "item-supplier", "item-supplier-custom", "item-serial", "item-comment")) {
            assertThat(html).as(id).contains("for=\"" + id + "\"").contains("id=\"" + id + "\"");
        }
        assertThat(html.split("<h1", -1)).hasSize(2);
    }

    @Test
    void anItemInFulfilmentLocksTheFieldsOnlyANewItemChangesAndSaysWhy() {
        // given
        OrderItem item = item(FulfilmentStatus.Ordered);
        item.setDeliveryId(DELIVERY_ID);
        Map<String, Object> variables = variables(item, false);
        variables.put("priceLocked", true);
        variables.put("serviceFlagLocked", true);
        variables.put("consolidationLocked", true);

        // when
        String html = render(variables);

        // then
        assertThat(html).contains("id=\"item-locked-note\"")
                .containsPattern("id=\"item-sku\"[^>]*disabled").containsPattern("id=\"item-qty\"[^>]*disabled")
                .containsPattern("id=\"item-cost\"[^>]*disabled").containsPattern("id=\"item-tax\"[^>]*disabled")
                .containsPattern("id=\"item-price\"[^>]*disabled").containsPattern("id=\"item-service\"[^>]*disabled")
                .containsPattern("id=\"item-consolidated\"[^>]*disabled")
                .contains("Po wystawieniu dokumentu ceny nie zmienisz.")
                .contains("Pozycja ma dostawcę — przełącznika usługi nie zmienisz.")
                .contains("łączenia na fakturze nie zmienisz")
                .doesNotContain("name=\"deliveryId\"").contains("Dostawa:")
                .contains("<a href=\"/dashboard/deliveries/details?deliveryId=" + DELIVERY_ID + "\">b58e2f14</a>")
                .doesNotContain(">" + DELIVERY_ID + "<").contains("Zamówiony");
        assertThat(html).doesNotContainPattern("id=\"item-name\"[^>]*disabled")
                .doesNotContainPattern("id=\"item-serial\"[^>]*disabled");
    }

    @Test
    void aNewItemOfAnInvoicedOrderShowsItsSaleFieldsReadOnlyWithTheReason() {
        // given
        Map<String, Object> variables = variables(item(FulfilmentStatus.New), false);
        variables.put("saleLock", ItemSaleLock.INVOICED);
        variables.put("priceLocked", true);
        variables.put("priceLockedKey", ItemSaleLock.INVOICED.priceKey());

        // when
        String html = render(variables);

        // then: read-only, not disabled, so the values are posted and the server sees them unchanged
        assertThat(html).contains("id=\"item-name\" type=\"text\" name=\"name\" value=\"Laptop Pro 14\" aria-describedby=\"item-name-help\" readonly=\"readonly\">")
                .contains("id=\"item-qty\" type=\"text\" inputmode=\"numeric\" name=\"qty\" value=\"2\" aria-describedby=\"item-sale-help\" readonly=\"readonly\">")
                .contains("id=\"item-tax\" type=\"text\" name=\"tax\" value=\"1.23\" aria-describedby=\"item-sale-help item-numbers-help\" readonly=\"readonly\">")
                .containsPattern("id=\"item-price\"[^>]*aria-describedby=\"item-price-help\"[^>]*disabled")
                .contains("<p class=\"cl-help\" id=\"item-name-help\">Zamówienie ma już fakturę albo paragon — nazwy nie zmienisz.</p>")
                .contains("<p class=\"cl-help\" id=\"item-sale-help\">Zamówienie ma już fakturę albo paragon — ilości ani stawki VAT nie zmienisz.</p>")
                .contains("Zamówienie ma już fakturę albo paragon — ceny nie zmienisz.")
                .doesNotContainPattern("id=\"item-name\"[^>]*disabled")
                .doesNotContainPattern("id=\"item-qty\"[^>]*disabled")
                .doesNotContainPattern("id=\"item-serial\"[^>]*(readonly|disabled)")
                .doesNotContainPattern("id=\"item-comment\"[^>]*(readonly|disabled)")
                .doesNotContainPattern("id=\"item-cost\"[^>]*(readonly|disabled)");
    }

    @Test
    void whileAnEReceiptIsBeingIssuedTheSaleFieldsSayWhyInItsOwnWords() {
        // given
        Map<String, Object> variables = variables(item(FulfilmentStatus.New), false);
        variables.put("saleLock", ItemSaleLock.RECEIPT_ISSUING);
        variables.put("priceLocked", true);
        variables.put("priceLockedKey", ItemSaleLock.RECEIPT_ISSUING.priceKey());

        // when
        String html = render(variables);

        // then
        assertThat(html).contains("Trwa wystawianie e-paragonu — nazwy nie zmienisz.")
                .contains("Trwa wystawianie e-paragonu — ilości ani stawki VAT nie zmienisz.")
                .contains("Trwa wystawianie e-paragonu — ceny nie zmienisz.")
                .containsPattern("id=\"item-name\"[^>]*readonly");
    }

    @Test
    void aFulfilledItemOfAnInvoicedOrderKeepsQuantityAndVatDisabledAndLocksTheName() {
        // given: quantity and VAT are already fixed by fulfilment (not posted at all), the name is the only sale field
        OrderItem item = item(FulfilmentStatus.Ordered);
        Map<String, Object> variables = variables(item, false);
        variables.put("saleLock", ItemSaleLock.INVOICED);
        variables.put("priceLocked", true);
        variables.put("priceLockedKey", ItemSaleLock.INVOICED.priceKey());

        // when
        String html = render(variables);

        // then
        assertThat(html).containsPattern("id=\"item-name\"[^>]*readonly")
                .containsPattern("id=\"item-qty\"[^>]*disabled").doesNotContainPattern("id=\"item-qty\"[^>]*readonly")
                .contains("id=\"item-tax\" type=\"text\" name=\"tax\" value=\"1.23\" disabled=\"disabled\">")
                .doesNotContain("id=\"item-sale-help\"");
    }

    @Test
    void aRefusedSaveShowsItsReasonAboveTheForm() {
        // given
        Map<String, Object> variables = variables(item(FulfilmentStatus.New), false);
        variables.put("itemError", "Nieznany dostawca");

        // when
        String html = render(variables);

        // then
        assertThat(html).contains("id=\"item-error\"").contains("data-cl-error-summary").contains("role=\"alert\"")
                .contains("Nieznany dostawca");
        assertThat(html.indexOf("id=\"item-error\"")).isLessThan(html.indexOf("id=\"order-item-form\""));
    }

    @Test
    void theItemOfAClosedOrderIsDescribedWithoutAForm() {
        // given
        OrderItem item = item(FulfilmentStatus.Delivered);
        item.setDeliveryId(DELIVERY_ID);
        item.setConsolidated(true);

        // when
        String html = render(variables(item, true));

        // then
        assertThat(html).doesNotContain("<form").doesNotContain("<input").doesNotContain("Zapisz")
                .contains("pozycję tylko oglądasz").contains("id=\"item-details\"")
                .contains("5901234123457").contains("LP14-2026").contains("SN-1, SN-2").contains("Klient prosi o fakturę na firmę")
                .contains("4 999,00 PLN").contains("3 500,00 PLN").contains("23%").contains("<dt>Dostawa</dt>")
                .contains("<a href=\"/dashboard/deliveries/details?deliveryId=" + DELIVERY_ID + "\">b58e2f14</a>")
                .contains("Wróć do zamówienia").contains(">Skompletowany</span>");
    }

    @Test
    void aNewItemWithAConnectedSupplierShowsItsLabelAndPreselectsItInThePicker() {
        // given
        OrderItem item = item(FulfilmentStatus.New);
        item.setDeliveryId("Acme");

        // when
        String html = render(variables(item, false));

        // then
        assertThat(html).contains("<span>Dostawca:</span>").contains("<option value=\"Acme\" selected=\"selected\">Acme</option>")
                .containsPattern("<select[^>]*id=\"item-supplier\"[^>]*name=\"deliveryId\"")
                .doesNotContain("id=\"item-delivery\"").doesNotContain("Dostawa:");
    }

    @Test
    void aNewItemHoldingADeliveryIdKeepsItInsteadOfOfferingItAsATypedSupplier() {
        // given: old data, the controller found a delivery with this id
        OrderItem item = item(FulfilmentStatus.New);
        item.setDeliveryId(DELIVERY_ID);
        Map<String, Object> variables = variables(item, false);
        variables.put("deliveryHeld", true);

        // when
        String html = render(variables);

        // then
        assertThat(html).contains("id=\"item-delivery\"").contains("Dostawa:")
                .contains("<input type=\"hidden\" name=\"deliveryId\" value=\"" + DELIVERY_ID + "\">")
                .doesNotContain("id=\"item-supplier\"").doesNotContain("name=\"customSupplier\"")
                .contains("dostawcy tu nie zmienisz");
    }

    @Test
    void aNewItemWithoutADeliveryHasNoSupplierInTheHeaderAndThePickerOnNone() {
        // when
        String html = render(variables(item(FulfilmentStatus.New), false));

        // then
        assertThat(html).doesNotContain("Dostawca:").doesNotContain("Dostawa:")
                .contains("<option value=\"\">Bez dostawcy</option>").doesNotContain("value=\"Acme\" selected")
                .doesNotContain("value=\"__custom__\" selected");
    }

    @Test
    void theHeaderDeliveryIsTheItemsTableCellForADeliveryASupplierAndNone() {
        // given
        pl.commercelink.orders.Order order = OrderDetailsTemplateTest.order(pl.commercelink.orders.OrderStatus.Realization);
        OrderItem ordered = item(FulfilmentStatus.New);
        ordered.markAsOrdered(DELIVERY_ID, 3500.0);
        OrderItem allocated = item(FulfilmentStatus.Allocation);
        allocated.setDeliveryId("HURT-ABC");
        OrderItem none = item(FulfilmentStatus.New);
        pl.commercelink.web.orders.OrderPageModelFactory factory = OrderDetailsTemplateTest.factory(java.util.Set.of());
        pl.commercelink.web.orders.OrderPageModelFactory.Viewer admin =
                new pl.commercelink.web.orders.OrderPageModelFactory.Viewer(false, true, null);

        // when
        OrderItemRow.Delivery delivery = factory.delivery(order, ordered, List.of(ordered), admin, OrderDetailsTemplateTest.PL);
        OrderItemRow.Delivery supplier = factory.delivery(order, allocated, List.of(allocated), admin, OrderDetailsTemplateTest.PL);
        OrderItemRow row = factory.build(order, List.of(ordered), admin, OrderDetailsTemplateTest.PL).items().products().get(0);

        // then
        assertThat(delivery).isEqualTo(new OrderItemRow.Delivery("b58e2f14",
                "/dashboard/deliveries/details?deliveryId=" + DELIVERY_ID, true));
        assertThat(delivery.label()).isEqualTo(row.deliveryLabel());
        assertThat(delivery.href()).isEqualTo(row.deliveryHref());
        assertThat(supplier.delivery()).isFalse();
        assertThat(supplier.label()).isEqualTo("HURT-ABC");
        assertThat(factory.delivery(order, none, List.of(none), admin, OrderDetailsTemplateTest.PL)).isNull();
    }

    @Test
    void amountsShowTwoDecimals() {
        // given
        OrderItem item = item(FulfilmentStatus.New);
        item.setPrice(649.0);
        item.setCost(448.5);

        // when
        String html = render(variables(item, false));

        // then: as the rest of the app shows amounts, not "649.0"; a cost stored with more decimals keeps them
        assertThat(html).contains("id=\"item-price\" type=\"text\" name=\"price\" value=\"649.00\"")
                .contains("id=\"item-cost\" type=\"text\" name=\"cost\" value=\"448.50\"")
                .contains("id=\"item-numbers-help\"");
        item.setCost(123.4567);
        assertThat(render(variables(item, false))).contains("name=\"cost\" value=\"123.4567\"");
    }

    @Test
    void amountHelpIsHiddenWhenNoAmountIsEditable() {
        // given: an item in fulfilment of an invoiced order, price locked by the document, cost and VAT fixed
        OrderItem item = item(FulfilmentStatus.Delivered);
        Map<String, Object> variables = variables(item, false);
        variables.put("saleLock", ItemSaleLock.INVOICED);
        variables.put("priceLocked", true);
        variables.put("priceLockedKey", ItemSaleLock.INVOICED.priceKey());

        // when
        String html = render(variables);

        // then
        assertThat(html).containsPattern("id=\"item-price\"[^>]*disabled").containsPattern("id=\"item-cost\"[^>]*disabled")
                .doesNotContain("id=\"item-numbers-help\"").doesNotContain("item-numbers-help")
                .doesNotContain("wpisuj z kropką");
    }

    @Test
    void theStoresWarehouseReadsInPolishInTheHeader() {
        // given
        Order order = OrderDetailsTemplateTest.order(OrderStatus.Realization);
        OrderItem item = item(FulfilmentStatus.Delivered);
        item.setDeliveryId(OrderItem.GENERIC_WAREHOUSE_ORDER_NO);
        OrderPageModelFactory factory = OrderDetailsTemplateTest.factory(Set.of());
        Map<String, Object> variables = variables(item, false);
        variables.put("delivery", factory.delivery(order, item, List.of(item),
                new OrderPageModelFactory.Viewer(false, true, null), OrderDetailsTemplateTest.PL));

        // when
        String html = render(variables);

        // then
        assertThat(html).contains("Magazyn sklepu").doesNotContain("Warehouse");
    }
}
