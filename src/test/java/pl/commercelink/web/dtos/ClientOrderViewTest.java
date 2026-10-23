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
import pl.commercelink.stores.FulfilmentConfiguration;
import pl.commercelink.stores.ShippingConfiguration;
import pl.commercelink.stores.Store;
import pl.commercelink.taxonomy.CategoryLocalizer;
import pl.commercelink.web.orders.Money;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ClientOrderViewTest {

    private static final String STORE_ID = "store-1";
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 22);

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
    @DisplayName("from hides that nothing was ordered when items are only allocated")
    void fromHidesUnorderedItemsWhenOnlyAllocated() {
        // given
        Order order = order(OrderStatus.Assembly);
        List<OrderItem> items = List.of(
                product(order, "A", "Cat", 1, FulfilmentStatus.New, 1),
                product(order, "B", "Cat", 1, FulfilmentStatus.Allocation, 2)
        );

        // when
        ClientOrderView view = ClientOrderView.from(order, items, store(), categoryLocalizer);

        // then
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
    @DisplayName("from names the pickup point carrier the way the shipments list does, not by its stored code")
    void fromNamesPickupPointCarrierForTheCustomer() {
        // given
        Order order = order(OrderStatus.Assembly);
        Shipment locker = new Shipment(ShipmentType.PickupPoint);
        locker.setCarrier("inpost");
        locker.setCollectionPointCode("KRA01A");
        order.setShipments(new LinkedList<>(List.of(locker)));

        // when
        ClientOrderView view = ClientOrderView.from(order, List.of(), store(), categoryLocalizer);

        // then
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
        assertThat(view.isStageDone(ClientOrderStage.Shipping)).isTrue();
        assertThat(view.isStageDone(ClientOrderStage.Delivered)).isTrue();
    }

    @Test
    @DisplayName("from marks every stage done for a collected personal-collection order")
    void fromMarksEveryStageDoneForCollectedOrder() {
        // given
        Order order = order(OrderStatus.Delivered);
        Shipment collected = new Shipment(ShipmentType.PersonalCollection);
        collected.setShippedAt(LocalDateTime.of(2026, 9, 12, 16, 40));
        collected.setDeliveredAt(LocalDateTime.of(2026, 9, 13, 9, 15));
        order.setShipments(new LinkedList<>(List.of(collected)));

        // when
        ClientOrderView view = ClientOrderView.from(order, List.of(), store(), categoryLocalizer);

        // then
        assertThat(view.isPersonalCollection()).isTrue();
        assertThat(view.isStageDone(ClientOrderStage.Shipping)).isTrue();
        assertThat(view.isStageDone(ClientOrderStage.Delivered)).isTrue();
        assertThat(view.getDeliveredAt()).isEqualTo(LocalDateTime.of(2026, 9, 13, 9, 15));
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

    @Test
    @DisplayName("from lists the days the store allows for the order's shipment type, as ISO numbers for the date input")
    void fromExposesPreferredShippingDaysOfTheShipmentType() {
        // given
        Order collection = order(OrderStatus.Assembly);
        collection.addShipment(new Shipment(ShipmentType.PersonalCollection));
        Order courier = order(OrderStatus.Assembly);
        courier.addShipment(new Shipment(ShipmentType.Courier));
        Store store = store();
        FulfilmentConfiguration configuration = new FulfilmentConfiguration();
        configuration.setPreferredShippingDays(Map.of("PersonalCollection", List.of("SATURDAY", "MONDAY")));
        store.setFulfilmentConfiguration(configuration);

        // when
        ClientOrderView collectionView = ClientOrderView.from(collection, List.of(), store, categoryLocalizer);
        ClientOrderView courierView = ClientOrderView.from(courier, List.of(), store, categoryLocalizer);

        // then
        assertThat(collectionView.getPreferredShippingDays()).containsExactly(DayOfWeek.MONDAY, DayOfWeek.SATURDAY);
        assertThat(collectionView.getPreferredShippingDayNumbers()).isEqualTo("1,6");
        assertThat(courierView.getPreferredShippingDayNumbers()).isEqualTo("1,2,3,4,5");
    }

    @Test
    void headlineFollowsTheStage() {
        // given
        Order accepted = order(OrderStatus.New);
        Order collecting = order(OrderStatus.Shipping);
        collecting.addShipment(new Shipment(ShipmentType.PersonalCollection));
        Order collected = order(OrderStatus.Delivered);
        collected.addShipment(new Shipment(ShipmentType.PersonalCollection));
        Order cancelled = order(OrderStatus.Cancelled);

        // when
        ClientOrderView acceptedView = ClientOrderView.from(accepted, List.of(), store(), categoryLocalizer);
        ClientOrderView collectingView = ClientOrderView.from(collecting, List.of(), store(), categoryLocalizer);
        ClientOrderView collectedView = ClientOrderView.from(collected, List.of(), store(), categoryLocalizer);
        ClientOrderView cancelledView = ClientOrderView.from(cancelled, List.of(), store(), categoryLocalizer);

        // then
        assertThat(acceptedView.getHeadlineKey()).isEqualTo("client.order.headline.accepted");
        assertThat(collectingView.getHeadlineKey()).isEqualTo("client.order.headline.collection");
        assertThat(collectedView.getHeadlineKey()).isEqualTo("client.order.headline.collected");
        assertThat(cancelledView.getHeadlineKey()).isEqualTo("client.order.headline.cancelled");
        assertThat(cancelledView.getLeadKey()).isNull();
        assertThat(acceptedView.isBeforeDispatch()).isTrue();
        assertThat(collectingView.isBeforeDispatch()).isFalse();
        assertThat(cancelledView.isBeforeDispatch()).isFalse();
    }

    @Test
    void leadForAssemblyDependsOnItems() {
        // given
        Order partial = order(OrderStatus.Assembly);
        Order awaiting = order(OrderStatus.Assembly);
        Order plain = order(OrderStatus.Assembly);

        // when
        ClientOrderView partialView = ClientOrderView.from(partial, List.of(
                product(partial, "A", "Cat", 1, FulfilmentStatus.Ordered, 1),
                product(partial, "B", "Cat", 1, FulfilmentStatus.Reserved, 2)), store(), categoryLocalizer);
        ClientOrderView awaitingView = ClientOrderView.from(awaiting, List.of(
                product(awaiting, "A", "Cat", 1, FulfilmentStatus.Ordered, 1)), store(), categoryLocalizer);
        ClientOrderView plainView = ClientOrderView.from(plain, List.of(
                product(plain, "A", "Cat", 1, FulfilmentStatus.New, 1)), store(), categoryLocalizer);

        // then
        assertThat(partialView.getLeadKey()).isEqualTo("client.order.lead.assembly.partial");
        assertThat(awaitingView.getLeadKey()).isEqualTo("client.order.lead.assembly.awaiting");
        assertThat(plainView.getLeadKey()).isEqualTo("client.order.lead.assembly");
    }

    @Test
    void shippingLeadPointsToTheCarrierOnlyWhenAShipmentHasATrackingLink() {
        // given
        Order tracked = order(OrderStatus.Shipping);
        Shipment withUrl = new Shipment(ShipmentType.Courier);
        withUrl.setCarrier("dpd");
        withUrl.setTrackingNo("1");
        withUrl.setTrackingUrl("https://track/1");
        withUrl.setShippedAt(LocalDateTime.of(2026, 10, 21, 10, 0));
        tracked.setShipments(new LinkedList<>(List.of(withUrl)));
        Order untracked = order(OrderStatus.Shipping);
        Shipment withoutUrl = new Shipment(ShipmentType.Courier);
        withoutUrl.setCarrier("dpd");
        withoutUrl.setTrackingNo("2");
        withoutUrl.setShippedAt(LocalDateTime.of(2026, 10, 21, 10, 0));
        untracked.setShipments(new LinkedList<>(List.of(withoutUrl)));
        Order noParcel = order(OrderStatus.Shipping);

        // when
        ClientOrderView trackedView = ClientOrderView.from(tracked, List.of(), store(), categoryLocalizer);
        ClientOrderView untrackedView = ClientOrderView.from(untracked, List.of(), store(), categoryLocalizer);
        ClientOrderView noParcelView = ClientOrderView.from(noParcel, List.of(), store(), categoryLocalizer);

        // then
        assertThat(trackedView.getLeadKey()).isEqualTo("client.order.lead.shipping");
        assertThat(untrackedView.getLeadKey()).isEqualTo("client.order.lead.shipping.noTracking");
        assertThat(noParcelView.getLeadKey()).isEqualTo("client.order.lead.shipping.noParcel");
    }

    @Test
    void preferredDaysCoverTheWindowOnAllowedWeekdays() {
        // given
        Order order = order(OrderStatus.Assembly);
        order.setEstimatedShippingAt(LocalDate.of(2026, 10, 23));

        // when
        ClientOrderView view = ClientOrderView.from(order, List.of(), store(), categoryLocalizer, false, true, TODAY);

        // then
        assertThat(view.getPreferredShippingOptions()).hasSize(11);
        assertThat(view.getPreferredShippingOptions().get(0))
                .isEqualTo(new ClientOrderView.DayOption(LocalDate.of(2026, 10, 23), true, false));
        assertThat(view.getPreferredShippingOptions().get(10).date()).isEqualTo(LocalDate.of(2026, 11, 6));
        assertThat(view.getPreferredShippingOptions()).filteredOn(ClientOrderView.DayOption::planned).hasSize(1);
        assertThat(view.isAsapSelected()).isTrue();
    }

    @Test
    void pastDaysAreNotOffered() {
        // given
        Order order = order(OrderStatus.Assembly);
        order.setEstimatedShippingAt(LocalDate.of(2026, 10, 20));

        // when
        ClientOrderView view = ClientOrderView.from(order, List.of(), store(), categoryLocalizer, false, true, TODAY);

        // then
        assertThat(view.getPreferredShippingOptions().get(0).date()).isEqualTo(TODAY);
        assertThat(view.getPreferredShippingOptions()).noneMatch(ClientOrderView.DayOption::planned);
    }

    @Test
    void savedDayOutsideTheWindowIsOfferedAndSelected() {
        // given
        Order order = order(OrderStatus.Assembly);
        order.setEstimatedShippingAt(LocalDate.of(2026, 10, 23));
        order.setPreferredShippingAt(LocalDate.of(2026, 11, 20));

        // when
        ClientOrderView view = ClientOrderView.from(order, List.of(), store(), categoryLocalizer, false, true, TODAY);

        // then
        List<ClientOrderView.DayOption> options = view.getPreferredShippingOptions();
        assertThat(options).hasSize(12);
        assertThat(options.get(11)).isEqualTo(new ClientOrderView.DayOption(LocalDate.of(2026, 11, 20), false, true));
        assertThat(options).extracting(ClientOrderView.DayOption::date).isSorted();
        assertThat(view.isAsapSelected()).isFalse();
    }

    @Test
    void asapSelectedWithoutPreferredDay() {
        // given
        Order order = order(OrderStatus.Assembly);
        order.setEstimatedShippingAt(LocalDate.of(2026, 10, 23));

        // when
        ClientOrderView view = ClientOrderView.from(order, List.of(), store(), categoryLocalizer, false, true, TODAY);

        // then
        assertThat(view.isAsapSelected()).isTrue();
        assertThat(view.getPreferredShippingOptions()).noneMatch(ClientOrderView.DayOption::selected);
    }

    @Test
    void noOptionsWithoutEstimatedShipping() {
        // given
        Order order = order(OrderStatus.Assembly);

        // when
        ClientOrderView view = ClientOrderView.from(order, List.of(), store(), categoryLocalizer, false, true, TODAY);

        // then
        assertThat(view.getPreferredShippingOptions()).isEmpty();
    }

    @Test
    void amountsAreFormatted() {
        // given
        Order order = order(OrderStatus.New);
        order.setTotalPrice(6245.99);
        order.addPayment(Payment.bankTransfer("REF-1", "Przelew", 1000.0));
        Order overpaid = order(OrderStatus.New);
        overpaid.setTotalPrice(100.0);
        overpaid.addPayment(Payment.bankTransfer("REF-2", "Przelew", 150.5));

        // when
        ClientOrderView view = ClientOrderView.from(order, List.of(), store(), categoryLocalizer);
        ClientOrderView overpaidView = ClientOrderView.from(overpaid, List.of(), store(), categoryLocalizer);

        // then
        assertThat(view.getTotalFormatted()).isEqualTo(Money.format(6245.99));
        assertThat(view.getTotalFormatted()).endsWith("245,99");
        assertThat(view.getPaidFormatted()).isEqualTo(Money.format(1000.0));
        assertThat(view.getUnpaidFormatted()).isEqualTo(Money.format(5245.99));
        assertThat(view.getUnpaidRaw()).isEqualTo("5245.99");
        assertThat(overpaidView.getOverpaidFormatted()).isEqualTo(Money.format(50.5));
    }

    @Test
    void bankShowsCurrencyOnlyWhenNotPln() {
        // given
        Order order = order(OrderStatus.New);
        order.setTotalPrice(100.0);
        Store pln = store();
        pln.setBankAccounts(new LinkedList<>(List.of(bankAccount("PLN"))));
        Store eur = store();
        eur.setBankAccounts(new LinkedList<>(List.of(bankAccount("EUR"))));

        // when
        ClientOrderView plnView = ClientOrderView.from(order, List.of(), pln, categoryLocalizer);
        ClientOrderView eurView = ClientOrderView.from(order, List.of(), eur, categoryLocalizer);

        // then
        assertThat(plnView.getBank().currency()).isNull();
        assertThat(eurView.getBank().currency()).isEqualTo("EUR");
        assertThat(eurView.getBank().ibanRaw()).isEqualTo("PL61109010140000071219812874");
        assertThat(eurView.getBank().ibanGrouped()).isEqualTo("PL61 1090 1014 0000 0712 1981 2874");
        assertThat(eurView.getBank().holder()).isEqualTo("Sklep sp. z o.o.");
        assertThat(eurView.getBank().transferTitle()).isEqualTo(order.getOrderId());
    }

    @Test
    void shipmentRowsUseCustomerCarrierNames() {
        // given
        Order order = order(OrderStatus.Shipping);
        Shipment courier = new Shipment(ShipmentType.Courier);
        courier.setCarrier("inpost");
        courier.setTrackingNo("123");
        courier.setTrackingUrl("https://track/123");
        courier.setShippedAt(LocalDateTime.of(2026, 10, 21, 10, 0));
        Shipment collection = new Shipment(ShipmentType.PersonalCollection);
        collection.setShippedAt(LocalDateTime.of(2026, 10, 20, 9, 0));
        order.setShipments(new LinkedList<>(List.of(courier, collection)));

        // when
        ClientOrderView view = ClientOrderView.from(order, List.of(), store(), categoryLocalizer);

        // then
        assertThat(view.getShipmentRows()).hasSize(2);
        assertThat(view.getShipmentRows().get(0).carrier()).isEqualTo("InPost");
        assertThat(view.getShipmentRows().get(0).trackingNo()).isEqualTo("123");
        assertThat(view.getShipmentRows().get(0).trackingUrl()).isEqualTo("https://track/123");
        assertThat(view.getReadyForCollectionAt()).isEqualTo(LocalDateTime.of(2026, 10, 20, 9, 0));
    }

    private static BankAccount bankAccount(String currency) {
        BankAccount account = new BankAccount();
        account.setIban("PL61109010140000071219812874");
        account.setAccountHolder("Sklep sp. z o.o.");
        account.setBankName("Bank");
        account.setSwiftCode("SWIFT");
        account.setCurrency(currency);
        account.set_default(true);
        return account;
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
