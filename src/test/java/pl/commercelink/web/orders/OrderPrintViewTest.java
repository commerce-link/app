package pl.commercelink.web.orders;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import pl.commercelink.documents.Document;
import pl.commercelink.documents.DocumentType;
import pl.commercelink.inventory.supplier.SupplierLabelMap;
import pl.commercelink.inventory.supplier.SupplierLabels;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.orders.Shipment;
import pl.commercelink.orders.ShipmentType;
import pl.commercelink.stores.ConnectionMode;
import pl.commercelink.stores.FulfilmentConfiguration;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoreSupplierConnection;
import pl.commercelink.stores.StoresRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class OrderPrintViewTest {

    static final String ORDER_ID = "3e373abc-1111-2222-3333-444455556666";

    static Store store() {
        Store store = new Store();
        store.setStoreId("store-1");
        store.setName("Demo");
        StoreSupplierConnection connection = new StoreSupplierConnection("manual-abcd1234", ConnectionMode.MANUAL);
        connection.setLabel("Hurtownia Kowalski");
        FulfilmentConfiguration fulfilment = new FulfilmentConfiguration();
        fulfilment.setSupplierConnections(new ArrayList<>(List.of(connection)));
        store.setFulfilmentConfiguration(fulfilment);
        return store;
    }

    static SupplierLabelMap labels() {
        return new SupplierLabels(mock(StoresRepository.class)).forStore(store());
    }

    static Order order() {
        Order order = new Order("store-1");
        order.setOrderId(ORDER_ID);
        order.setOrderedAt(LocalDateTime.of(2026, 9, 27, 21, 33, 40));
        return order;
    }

    static OrderItem item(String name, boolean service) {
        OrderItem item = new OrderItem(ORDER_ID, "CPU", name, 2, 749, "SKU", false, 0);
        item.setService(service);
        return item;
    }

    @Test
    void theCardNamesTheSupplierOfAConnectionAndShortensARealDeliveryId() {
        // given
        OrderItem fromConnection = item("AMD Ryzen 7", false);
        fromConnection.setDeliveryId("manual-abcd1234");
        OrderItem fromDelivery = item("Pamięć", false);
        fromDelivery.setDeliveryId("7d1c0a2b-aaaa-bbbb-cccc-000000000001");
        OrderItem undelivered = item("Montaż", true);

        // when
        OrderPrintView.Card card = OrderPrintView.card(order(), List.of(fromConnection, fromDelivery, undelivered),
                OrderLinks.of(order(), false), labels());

        // then
        assertThat(card.items()).extracting(OrderPrintView.ItemRow::delivery)
                .containsExactly("Hurtownia Kowalski", "7d1c0a2b", null);
    }

    @Test
    void theCardFormatsDatesLikeTheDetailsPageAndKeepsEveryDocumentAndShipment() {
        // given
        Order order = order();
        order.setComment("  Zadzwonić przed wysyłką\nbrama od podwórza  ");
        order.getDocuments().add(new Document("d1", "FV/6/2026", null, DocumentType.InvoiceVat));
        order.getDocuments().add(new Document("d2", null, null, DocumentType.GoodsIssue));
        Shipment shipped = new Shipment(ShipmentType.Courier);
        shipped.setCarrier("InPost");
        shipped.setTrackingNo("E2E0000000001");
        shipped.setShippedAt(LocalDateTime.of(2026, 9, 26, 21, 33));
        order.getShipments().add(shipped);
        order.getShipments().add(new Shipment(ShipmentType.PersonalCollection));

        // when
        OrderPrintView.Card card = OrderPrintView.card(order, List.of(), OrderLinks.of(order, false), labels());

        // then
        assertThat(card.orderedAt()).isEqualTo("27.09.2026, 21:33");
        assertThat(card.comment()).isEqualTo("Zadzwonić przed wysyłką\nbrama od podwórza");
        assertThat(card.documents()).containsExactly(
                new OrderPrintView.DocumentRow("FV/6/2026", "DocumentType.InvoiceVat"),
                new OrderPrintView.DocumentRow(null, "DocumentType.GoodsIssue"));
        assertThat(card.shipments()).containsExactly(
                new OrderPrintView.ShipmentRow("ShipmentType.Courier", "E2E0000000001", "InPost", "26.09.2026, 21:33"),
                new OrderPrintView.ShipmentRow("ShipmentType.PersonalCollection", null, null, null));
    }

    @Test
    void theCardOfASuperAdminLinksToTheStoreScopedOrder() {
        // when
        OrderPrintView.Card card = OrderPrintView.card(order(), List.of(), OrderLinks.of(order(), true), labels());

        // then
        assertThat(card.detailsHref()).isEqualTo("/dashboard/store/store-1/orders/" + ORDER_ID);
        assertThat(card.shortId()).isEqualTo("3e373abc");
    }

    @Test
    void theCardSurvivesAnOrderWithoutDocumentAndShipmentLists() {
        // given: an old record read from DynamoDB may lack both attributes
        Order order = order();
        order.setDocuments(null);
        order.setShipments(null);

        // when
        OrderPrintView.Card card = OrderPrintView.card(order, List.of(), OrderLinks.of(order, false), labels());

        // then
        assertThat(card.documents()).isEmpty();
        assertThat(card.shipments()).isEmpty();
        assertThat(card.comment()).isNull();
    }

    @Test
    void theCollectionProtocolListsProductsOnlyWithTheStoreAndTheDate() {
        // given
        OrderItem product = item("AMD Ryzen 7", false);
        product.setSerialNo("SN-1, SN-2");
        OrderItem service = item("Montaż", true);

        // when
        OrderPrintView.Collection collection = OrderPrintView.collection(order(), List.of(product, service), store(),
                LocalDate.of(2026, 9, 28), "Kraków, PL", OrderLinks.of(order(), false), labels());

        // then
        assertThat(collection.items()).extracting(OrderPrintView.ItemRow::name).containsExactly("AMD Ryzen 7");
        assertThat(collection.items().get(0).serialNo()).isEqualTo("SN-1, SN-2");
        assertThat(collection.store()).isEqualTo("Demo");
        assertThat(collection.date()).isEqualTo("28.09.2026");
        assertThat(collection.location()).isEqualTo("Kraków, PL");
        assertThat(collection.detailsHref()).isEqualTo("/dashboard/orders/" + ORDER_ID);
    }

    @Test
    void warehouseDeliveryReadsAsTheStoresWarehouse() {
        // given: the labels as OrdersController hands them to both printouts
        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding("UTF-8");
        messages.setFallbackToSystemLocale(false);
        SupplierLabelMap labels = labels().withWarehouse(
                OrderPageModelFactory.warehouseLabel(messages, Locale.forLanguageTag("pl")));
        OrderItem fromWarehouse = item("Pamięć", false);
        fromWarehouse.setDeliveryId(OrderItem.GENERIC_WAREHOUSE_ORDER_NO);
        OrderItem fromConnection = item("AMD Ryzen 7", false);
        fromConnection.setDeliveryId("manual-abcd1234");

        // when
        OrderPrintView.Card card = OrderPrintView.card(order(), List.of(fromWarehouse, fromConnection),
                OrderLinks.of(order(), false), labels);
        OrderPrintView.Collection collection = OrderPrintView.collection(order(), List.of(fromWarehouse), store(),
                LocalDate.of(2026, 9, 29), "Kraków, PL", OrderLinks.of(order(), false), labels);

        // then
        assertThat(card.items()).extracting(OrderPrintView.ItemRow::delivery)
                .containsExactly("Magazyn sklepu", "Hurtownia Kowalski");
        assertThat(collection.items()).extracting(OrderPrintView.ItemRow::delivery).containsExactly("Magazyn sklepu");
    }
}
