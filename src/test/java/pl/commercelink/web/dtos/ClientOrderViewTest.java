package pl.commercelink.web.dtos;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pl.commercelink.orders.BillingDetails;
import pl.commercelink.orders.ClientOrderItemStatus;
import pl.commercelink.orders.ClientOrderStage;
import pl.commercelink.orders.FulfilmentStatus;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.orders.OrderStatus;
import pl.commercelink.orders.Payment;
import pl.commercelink.orders.Shipment;
import pl.commercelink.orders.ShipmentType;
import pl.commercelink.orders.ShippingDetails;
import pl.commercelink.stores.BankAccount;
import pl.commercelink.stores.ClientNotificationsConfiguration;
import pl.commercelink.stores.DeliveryOption;
import pl.commercelink.stores.ShippingConfiguration;
import pl.commercelink.stores.Store;
import pl.commercelink.taxonomy.CategoryLocalizer;

import java.time.LocalDateTime;
import java.util.LinkedList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ClientOrderViewTest {

    private static final String STORE_ID = "store-1";

    @Mock
    private CategoryLocalizer categoryLocalizer;

    @Test
    @DisplayName("from lists only product items in position order with localized categories and customer statuses")
    void fromListsOnlyProductsInPositionOrder() {
        // given
        when(categoryLocalizer.localize(eq("Monitory"), anyString())).thenReturn("Monitor");
        when(categoryLocalizer.localize(eq("Kable"), anyString())).thenReturn("Kabel");
        Order order = order(OrderStatus.Assembly);
        List<OrderItem> items = List.of(
                product(order, "Kabel DP", "Kable", 2, FulfilmentStatus.Delivered, 2),
                product(order, "Monitor Dell", "Monitory", 1, FulfilmentStatus.Ordered, 1),
                deliveryService(order, "Kurier DPD")
        );

        // when
        ClientOrderView view = ClientOrderView.from(order, items, store(), categoryLocalizer);

        // then
        assertThat(view.getItems()).extracting(ClientOrderView.ClientOrderItemView::name).containsExactly("Monitor Dell", "Kabel DP");
        assertThat(view.getItems()).extracting(ClientOrderView.ClientOrderItemView::category).containsExactly("Monitor", "Kabel");
        assertThat(view.getItems()).extracting(ClientOrderView.ClientOrderItemView::status)
                .containsExactly(ClientOrderItemStatus.AwaitingDelivery, ClientOrderItemStatus.Assembled);
        assertThat(view.getProductCount()).isEqualTo(2);
        assertThat(view.getAssembledCount()).isEqualTo(1);
        assertThat(view.getDeliveryMethod()).isEqualTo("Kurier DPD");
    }

    @Test
    @DisplayName("from picks the partial-assembly note when some items await delivery and others are assembled")
    void fromPicksPartialAssemblyNote() {
        // given
        Order order = order(OrderStatus.Assembly);
        List<OrderItem> items = List.of(
                product(order, "A", "Cat", 1, FulfilmentStatus.Ordered, 1),
                product(order, "B", "Cat", 1, FulfilmentStatus.Reserved, 2)
        );

        // when
        ClientOrderView view = ClientOrderView.from(order, items, store(), categoryLocalizer);

        // then
        assertThat(view.getStage()).isEqualTo(ClientOrderStage.Assembly);
        assertThat(view.getNoteKey()).isEqualTo("client.order.note.assembly.partial");
    }

    @Test
    @DisplayName("from picks the awaiting note when every item is still on its way from the supplier")
    void fromPicksAwaitingNoteWhenNothingAssembledYet() {
        // given
        Order order = order(OrderStatus.Assembly);
        List<OrderItem> items = List.of(product(order, "A", "Cat", 1, FulfilmentStatus.Ordered, 1));

        // when
        ClientOrderView view = ClientOrderView.from(order, items, store(), categoryLocalizer);

        // then
        assertThat(view.getNoteKey()).isEqualTo("client.order.note.assembly.awaiting");
    }

    @Test
    @DisplayName("from picks the plain assembly note when items are only allocated, hiding that nothing was ordered")
    void fromPicksPlainAssemblyNoteWhenItemsOnlyAllocated() {
        // given
        Order order = order(OrderStatus.Assembly);
        List<OrderItem> items = List.of(
                product(order, "A", "Cat", 1, FulfilmentStatus.New, 1),
                product(order, "B", "Cat", 1, FulfilmentStatus.Allocation, 2)
        );

        // when
        ClientOrderView view = ClientOrderView.from(order, items, store(), categoryLocalizer);

        // then
        assertThat(view.getNoteKey()).isEqualTo("client.order.note.assembly");
        assertThat(view.getItems()).extracting(ClientOrderView.ClientOrderItemView::status)
                .containsOnly(ClientOrderItemStatus.Allocation);
    }

    @Test
    @DisplayName("from marks a cancelled order with no stage and the cancelled note")
    void fromMarksCancelledOrder() {
        // given
        Order order = order(OrderStatus.Cancelled);

        // when
        ClientOrderView view = ClientOrderView.from(order, List.of(), store(), categoryLocalizer);

        // then
        assertThat(view.isCancelled()).isTrue();
        assertThat(view.getStage()).isNull();
        assertThat(view.getNoteKey()).isEqualTo("client.order.note.cancelled");
        assertThat(view.isStageDone(ClientOrderStage.Accepted)).isFalse();
    }

    @Test
    @DisplayName("from exposes only dispatched shipments with the earliest dispatch date and the collection note for pickup orders")
    void fromExposesDispatchedShipmentsAndCollectionNote() {
        // given
        Order order = order(OrderStatus.Shipping);
        Shipment collected = new Shipment(ShipmentType.PersonalCollection);
        collected.setShippedAt(LocalDateTime.of(2026, 9, 12, 16, 40));
        Shipment pending = new Shipment(ShipmentType.PersonalCollection);
        order.setShipments(new LinkedList<>(List.of(pending, collected)));

        // when
        ClientOrderView view = ClientOrderView.from(order, List.of(), store(), categoryLocalizer);

        // then
        assertThat(view.isPersonalCollection()).isTrue();
        assertThat(view.isShipmentsVisible()).isFalse();
        assertThat(view.getShipments()).containsExactly(collected);
        assertThat(view.getShippedAt()).isEqualTo(LocalDateTime.of(2026, 9, 12, 16, 40));
        assertThat(view.getDeliveredAt()).isNull();
        assertThat(view.getNoteKey()).isEqualTo("client.order.note.ready.for.collection");
        assertThat(view.getPickupAddress().city()).isEqualTo("Kraków");
    }

    @Test
    @DisplayName("from exposes the carrier and collection point code for a pickup-point order and keeps shipments visible")
    void fromExposesCarrierAndPointCodeForPickupPointOrder() {
        // given
        Order order = order(OrderStatus.Shipping);
        Shipment locker = new Shipment(ShipmentType.PickupPoint);
        locker.setCarrier("InPost");
        locker.setTrackingNo("TRK-1");
        locker.setCollectionPointCode("KRA01A");
        locker.setShippedAt(LocalDateTime.of(2026, 9, 12, 10, 0));
        order.setShipments(new LinkedList<>(List.of(locker)));

        // when
        ClientOrderView view = ClientOrderView.from(order, List.of(), store(), categoryLocalizer);

        // then
        assertThat(view.isPickupPoint()).isTrue();
        assertThat(view.isPersonalCollection()).isFalse();
        assertThat(view.isShipmentsVisible()).isTrue();
        assertThat(view.getCollectionPointCode()).isEqualTo("KRA01A");
        assertThat(view.getCarrier()).isEqualTo("InPost");
    }

    @Test
    @DisplayName("from keeps shipments visible for a courier order that is not cancelled")
    void fromKeepsShipmentsVisibleForCourierOrder() {
        // given
        Order order = order(OrderStatus.Assembly);

        // when
        ClientOrderView view = ClientOrderView.from(order, List.of(), store(), categoryLocalizer);

        // then
        assertThat(view.isPickupPoint()).isFalse();
        assertThat(view.isShipmentsVisible()).isTrue();
        assertThat(view.getCollectionPointCode()).isNull();
        assertThat(view.getCarrier()).isNull();
    }

    @Test
    @DisplayName("from reports the delivery date only once every shipment has been delivered")
    void fromReportsDeliveryDateOnlyWhenFullyDelivered() {
        // given
        Order order = order(OrderStatus.Delivered);
        Shipment first = new Shipment(ShipmentType.Courier);
        first.setCarrier("DPD");
        first.setTrackingNo("111");
        first.setShippedAt(LocalDateTime.of(2026, 9, 12, 10, 0));
        first.setDeliveredAt(LocalDateTime.of(2026, 9, 14, 10, 0));
        Shipment second = new Shipment(ShipmentType.Courier);
        second.setCarrier("DPD");
        second.setTrackingNo("222");
        second.setShippedAt(LocalDateTime.of(2026, 9, 12, 11, 0));
        second.setDeliveredAt(LocalDateTime.of(2026, 9, 15, 11, 5));
        order.setShipments(new LinkedList<>(List.of(first, second)));

        // when
        ClientOrderView view = ClientOrderView.from(order, List.of(), store(), categoryLocalizer);

        // then
        assertThat(view.getDeliveredAt()).isEqualTo(LocalDateTime.of(2026, 9, 15, 11, 5));
        assertThat(view.getNoteKey()).isEqualTo("client.order.note.delivered");
    }

    @Test
    @DisplayName("from reports no payment when the order carries only zero-amount payments")
    void fromReportsNoPaymentWithoutRecordedAmount() {
        // given
        Order order = order(OrderStatus.New);
        order.setTotalPrice(300.0);
        order.addPayment(new Payment());

        // when
        ClientOrderView view = ClientOrderView.from(order, List.of(), store(), categoryLocalizer);

        // then
        assertThat(view.isPaymentRecorded()).isFalse();
    }

    @Test
    @DisplayName("from exposes the remaining amount when a partial payment was recorded")
    void fromExposesRemainingAmountForPartialPayment() {
        // given
        Order order = order(OrderStatus.New);
        order.setTotalPrice(300.0);
        order.addPayment(Payment.bankTransfer("REF-1", "Zaliczka", 100.0));

        // when
        ClientOrderView view = ClientOrderView.from(order, List.of(), store(), categoryLocalizer);

        // then
        assertThat(view.isPaymentRecorded()).isTrue();
        assertThat(view.isFullyPaid()).isFalse();
        assertThat(view.getPaidAmount()).isEqualTo(100.0);
        assertThat(view.getUnpaidAmount()).isEqualTo(200.0);
        assertThat(view.isPaymentDue()).isTrue();
    }

    @Test
    @DisplayName("from shows the store's default bank account only while a payment is due")
    void fromShowsBankAccountOnlyWhilePaymentDue() {
        // given
        Store store = store();
        BankAccount account = new BankAccount();
        account.setIban("PL61109010140000071219812874");
        account.set_default(true);
        store.setBankAccounts(new LinkedList<>(List.of(account)));
        Order partial = order(OrderStatus.New);
        partial.setTotalPrice(300.0);
        partial.addPayment(Payment.bankTransfer("REF-1", "Zaliczka", 100.0));
        Order unpaid = order(OrderStatus.New);
        unpaid.setTotalPrice(300.0);
        Order overpaid = order(OrderStatus.New);
        overpaid.setTotalPrice(300.0);
        overpaid.addPayment(Payment.bankTransfer("REF-2", "Przelew", 350.0));

        // when
        ClientOrderView partialView = ClientOrderView.from(partial, List.of(), store, categoryLocalizer);
        ClientOrderView unpaidView = ClientOrderView.from(unpaid, List.of(), store, categoryLocalizer);
        ClientOrderView overpaidView = ClientOrderView.from(overpaid, List.of(), store, categoryLocalizer);

        // then
        assertThat(partialView.isBankAccountVisible()).isTrue();
        assertThat(partialView.getBankAccount().getIban()).isEqualTo("PL61109010140000071219812874");
        assertThat(unpaidView.isBankAccountVisible()).isFalse();
        assertThat(overpaidView.isBankAccountVisible()).isFalse();
    }

    @Test
    @DisplayName("from marks the order as fully paid once payments cover the total")
    void fromMarksOrderFullyPaid() {
        // given
        Order order = order(OrderStatus.New);
        order.setTotalPrice(300.0);
        order.addPayment(Payment.bankTransfer("REF-1", "Przelew", 300.0));

        // when
        ClientOrderView view = ClientOrderView.from(order, List.of(), store(), categoryLocalizer);

        // then
        assertThat(view.isPaymentRecorded()).isTrue();
        assertThat(view.isFullyPaid()).isTrue();
        assertThat(view.isOverpaid()).isFalse();
        assertThat(view.getUnpaidAmount()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("from exposes the overpaid amount when payments exceed the total")
    void fromExposesOverpaidAmount() {
        // given
        Order order = order(OrderStatus.New);
        order.setTotalPrice(300.0);
        order.addPayment(Payment.bankTransfer("REF-1", "Przelew", 350.0));

        // when
        ClientOrderView view = ClientOrderView.from(order, List.of(), store(), categoryLocalizer);

        // then
        assertThat(view.isFullyPaid()).isFalse();
        assertThat(view.isOverpaid()).isTrue();
        assertThat(view.getPaidAmount()).isEqualTo(350.0);
        assertThat(view.getOverpaidAmount()).isEqualTo(50.0);
    }

    @Test
    @DisplayName("from prefers the reply-to address from notification settings and falls back to the company e-mail")
    void fromResolvesContactEmail() {
        // given
        Order order = order(OrderStatus.New);
        Store withReplyTo = store();
        ClientNotificationsConfiguration notifications = new ClientNotificationsConfiguration();
        notifications.setReplyToEmail("pomoc@sklep.pl");
        withReplyTo.setClientNotificationsConfiguration(notifications);
        Store withoutReplyTo = store();
        withoutReplyTo.setClientNotificationsConfiguration(null);

        // when
        ClientOrderView preferred = ClientOrderView.from(order, List.of(), withReplyTo, categoryLocalizer);
        ClientOrderView fallback = ClientOrderView.from(order, List.of(), withoutReplyTo, categoryLocalizer);

        // then
        assertThat(preferred.getContactEmail()).isEqualTo("pomoc@sklep.pl");
        assertThat(fallback.getContactEmail()).isEqualTo("biuro@sklep.pl");
    }

    private static Order order(OrderStatus status) {
        Order order = new Order(STORE_ID);
        order.setStatus(status);
        order.setShippingDetails(ShippingDetails._default());
        return order;
    }

    private static OrderItem product(Order order, String name, String category, int qty, FulfilmentStatus status, int position) {
        OrderItem item = new OrderItem(order.getOrderId(), category, name, qty, 100.0, null, false, position);
        item.setStatus(status);
        return item;
    }

    private static OrderItem deliveryService(Order order, String name) {
        DeliveryOption option = new DeliveryOption();
        option.setName(name);
        return OrderItem.fromDeliveryOption(order.getOrderId(), option);
    }

    private static Store store() {
        Store store = new Store();
        store.setStoreId(STORE_ID);
        store.setName("Sklep");
        BillingDetails company = new BillingDetails();
        company.setEmail("biuro@sklep.pl");
        company.setCompanyName("Sklep sp. z o.o.");
        store.setBillingDetails(company);
        ShippingDetails pickup = ShippingDetails._default();
        pickup.setCompanyName("Sklep sp. z o.o.");
        pickup.setStreetAndNumber("Odbiorowa 1");
        pickup.setPostalCode("30-001");
        pickup.setCity("Kraków");
        ShippingConfiguration shippingConfiguration = new ShippingConfiguration();
        shippingConfiguration.setPickUpAddresses(new LinkedList<>(List.of(pickup)));
        store.setShippingConfiguration(shippingConfiguration);
        return store;
    }
}
