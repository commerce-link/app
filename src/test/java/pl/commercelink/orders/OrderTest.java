package pl.commercelink.orders;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import pl.commercelink.documents.Document;
import pl.commercelink.documents.DocumentType;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OrderTest {

    @Test
    @DisplayName("getLatestPayment returns null when order has no payments")
    void latestPaymentIsNullWhenNoPayments() {
        Order order = new Order("store-1");

        assertThat(order.getLatestPayment()).isNull();
    }

    @Test
    @DisplayName("getLatestPayment returns the most recent payment")
    void latestPaymentReturnsLastPayment() {
        Order order = new Order("store-1");
        order.setPayments(new java.util.LinkedList<>(java.util.List.of(
                Payment.bankTransfer("REF-1", "First", 10),
                Payment.bankTransfer("REF-2", "Second", 20))));

        assertThat(order.getLatestPayment().getReferenceNo()).isEqualTo("REF-2");
    }

    @Test
    @DisplayName("canBeSplit allows every status before realization and none from realization on")
    void canBeSplitDependsOnStatus() {
        for (OrderStatus status : OrderStatus.values()) {
            // given
            Order order = new Order("store-1");
            order.setStatus(status);

            // when / then
            boolean expected = status.isOneOf(OrderStatus.New, OrderStatus.Blocked, OrderStatus.Assembly, OrderStatus.Assembled);
            assertThat(order.canBeSplit()).as(status.name()).isEqualTo(expected);
        }
    }

    @Test
    @DisplayName("createClientOrderUrl builds the public status page link under the given domain")
    void createClientOrderUrlBuildsPublicLink() {
        // given
        Order order = new Order("store-1");
        order.setOrderId("order-1");

        // when
        String url = order.createClientOrderUrl("https://app.example.com");

        // then
        assertThat(url).isEqualTo("https://app.example.com/store/store-1/client/order/order-1");
    }

    @Test
    @DisplayName("getIssuableDocumentTypes returns empty for non-B2B order")
    void returnsEmptyForNonB2B() {
        Order order = b2cOrder();

        assertThat(order.getIssuableDocumentTypes()).isEmpty();
    }

    @Test
    @DisplayName("getIssuableDocumentTypes returns Order and InvoiceVat when no documents exist")
    void returnsOrderAndVatWhenNoDocuments() {
        Order order = b2bOrder();

        assertThat(order.getIssuableDocumentTypes())
                .containsExactly(DocumentType.Order, DocumentType.InvoiceVat);
    }

    @Test
    @DisplayName("getIssuableDocumentTypes excludes advance when order document exists but no payment received")
    void excludesAdvanceWithoutPayment() {
        Order order = b2bOrder();
        order.addDocument(orderDocument());

        assertThat(order.getIssuableDocumentTypes())
                .containsExactly(DocumentType.InvoiceVat);
    }

    @Test
    @DisplayName("getIssuableDocumentTypes offers advance when order document exists and payment received")
    void offersAdvanceWithOrderAndPayment() {
        Order order = b2bOrder();
        order.addDocument(orderDocument());
        order.addPayment(Payment.bankTransfer("ref-1", "Jan", 50.0));

        assertThat(order.getIssuableDocumentTypes())
                .containsExactly(DocumentType.InvoiceVat, DocumentType.InvoiceAdvance);
    }

    @Test
    @DisplayName("getIssuableDocumentTypes returns only final invoice when advance invoice exists")
    void returnsFinalWhenAdvanceExists() {
        Order order = b2bOrder();
        order.addDocument(orderDocument());
        order.addDocument(new Document("adv-1", "ZAL/1/2026", null, DocumentType.InvoiceAdvance));

        assertThat(order.getIssuableDocumentTypes())
                .containsExactly(DocumentType.InvoiceFinal);
    }

    @Test
    @DisplayName("getIssuableDocumentTypes returns empty once a closing invoice exists")
    void returnsEmptyWhenInvoiced() {
        Order order = b2bOrder();
        order.addDocument(new Document("vat-1", "FV/1/2026", null, DocumentType.InvoiceVat));

        assertThat(order.getIssuableDocumentTypes()).isEmpty();
    }

    @Test
    @DisplayName("removeDocument removes an invoice matched by type and number")
    void removeDocumentRemovesInvoiceMatchedByTypeAndNumber() {
        // given
        Order order = b2bOrder();
        order.addDocument(new Document("vat-1", "FV/1/2026", null, DocumentType.InvoiceVat));

        // when
        boolean removed = order.removeDocument(DocumentType.InvoiceVat, "FV/1/2026");

        // then
        assertThat(removed).isTrue();
        assertThat(order.getDocuments()).isEmpty();
        assertThat(order.isInvoiced()).isFalse();
    }

    @Test
    @DisplayName("removeDocument refuses to remove a warehouse document")
    void removeDocumentRefusesWarehouseDocument() {
        // given
        Order order = b2bOrder();
        order.addDocument(new Document("wz-1", "WZ/1/2026", null, DocumentType.GoodsIssue));

        // when
        boolean removed = order.removeDocument(DocumentType.GoodsIssue, "WZ/1/2026");

        // then
        assertThat(removed).isFalse();
        assertThat(order.getDocuments()).hasSize(1);
    }

    @Test
    @DisplayName("removeDocument refuses to remove an order document")
    void removeDocumentRefusesOrderDocument() {
        // given
        Order order = b2bOrder();
        order.addDocument(orderDocument());

        // when
        boolean removed = order.removeDocument(DocumentType.Order, "ZAM/1/2026");

        // then
        assertThat(removed).isFalse();
        assertThat(order.getDocuments()).hasSize(1);
    }

    @Test
    @DisplayName("removeDocument returns false when no document matches the number")
    void removeDocumentReturnsFalseWhenNumberDoesNotMatch() {
        // given
        Order order = b2bOrder();
        order.addDocument(new Document("vat-1", "FV/1/2026", null, DocumentType.InvoiceVat));

        // when
        boolean removed = order.removeDocument(DocumentType.InvoiceVat, "FV/2/2026");

        // then
        assertThat(removed).isFalse();
        assertThat(order.getDocuments()).hasSize(1);
    }

    private Order b2bOrder() {
        Order order = new Order("store-1");
        order.setOrderId("order-1");
        order.setTotalPrice(100.0);
        BillingDetails billing = new BillingDetails();
        billing.setTaxId("1234567890");
        order.setBillingDetails(billing);
        return order;
    }

    private Order b2cOrder() {
        Order order = new Order("store-1");
        order.setOrderId("order-1");
        order.setBillingDetails(new BillingDetails());
        return order;
    }

    private Document orderDocument() {
        return new Document("ord-1", "ZAM/1/2026", null, DocumentType.Order);
    }

    @Test
    @DisplayName("canChangeFulfilmentType allows the change while every product item is New")
    void canChangeFulfilmentTypeWhenAllProductsAreNew() {
        // given
        Order order = splittableOrder();
        OrderItem product = new OrderItem();
        product.setStatus(FulfilmentStatus.New);

        // when / then
        assertThat(order.canChangeFulfilmentType(java.util.List.of(product))).isTrue();
    }

    @Test
    @DisplayName("canChangeFulfilmentType ignores service items that are delivered from the start")
    void canChangeFulfilmentTypeIgnoresServices() {
        // given
        Order order = splittableOrder();
        OrderItem product = new OrderItem();
        product.setStatus(FulfilmentStatus.New);
        OrderItem service = new OrderItem();
        service.setService(true);
        service.setStatus(FulfilmentStatus.Delivered);

        // when / then
        assertThat(order.canChangeFulfilmentType(java.util.List.of(product, service))).isTrue();
    }

    @Test
    @DisplayName("canChangeFulfilmentType blocks the change once a product item left the New status")
    void canChangeFulfilmentTypeBlockedByAllocatedProduct() {
        // given
        Order order = splittableOrder();
        OrderItem fresh = new OrderItem();
        fresh.setStatus(FulfilmentStatus.New);
        OrderItem allocated = new OrderItem();
        allocated.setStatus(FulfilmentStatus.Allocation);

        // when / then
        assertThat(order.canChangeFulfilmentType(java.util.List.of(fresh, allocated))).isFalse();
    }

    private Order splittableOrder() {
        Order order = new Order("store-1");
        order.setOrderId("order-1");
        order.setBillingDetails(new BillingDetails());
        order.setStatus(OrderStatus.New);
        return order;
    }

    @Test
    @DisplayName("createSplit keeps the supplier the marketplace routed to")
    void splitOrderKeepsTheSupplierTheMarketplaceRoutedTo() {
        // given
        Order order = new Order("store-1");
        order.setOrderId("order-1");
        order.setBillingDetails(new BillingDetails());
        order.setShippingDetails(new ShippingDetails());
        order.setExternalSupplierId("2");

        // when
        Order split = order.createSplit();

        // then
        assertThat(split.getExternalSupplierId()).isEqualTo("2");
        assertThat(split.isBoundToExternalSupplier()).isTrue();
    }

    @Test
    @DisplayName("goods shipped by the supplier leave on the delivery day, with no in-house handling added")
    void shipsOnTheDeliveryDayWhenTheSupplierShipsToTheCustomer() {
        // given
        Order order = new Order("store-1");
        order.setOrderRealizationDays(3);

        // when
        order.updateEstimatedAssemblyAt(java.time.LocalDate.of(2026, 9, 14), true);

        // then
        assertThat(order.getEstimatedAssemblyAt()).isEqualTo(java.time.LocalDate.of(2026, 9, 14));
        assertThat(order.getEstimatedShippingAt()).isEqualTo(java.time.LocalDate.of(2026, 9, 14));
    }

    @Test
    @DisplayName("goods passing through the warehouse still add the realization days as working days")
    void addsRealizationDaysWhenTheGoodsPassThroughTheWarehouse() {
        // given
        Order order = new Order("store-1");
        order.setOrderRealizationDays(3);

        // when
        order.updateEstimatedAssemblyAt(java.time.LocalDate.of(2026, 9, 14), false);

        // then
        assertThat(order.getEstimatedAssemblyAt()).isEqualTo(java.time.LocalDate.of(2026, 9, 14));
        assertThat(order.getEstimatedShippingAt()).isEqualTo(java.time.LocalDate.of(2026, 9, 17));
    }

    @Test
    @DisplayName("a later leg that does not move the assembly date still puts the handling time back")
    void recomputesTheShippingDateWhenTheAssemblyDateDoesNotMove() {
        // given: the dropship leg was confirmed first, for a date later than the warehouse one
        Order order = new Order("store-1");
        order.setOrderRealizationDays(3);
        order.updateEstimatedAssemblyAt(java.time.LocalDate.of(2026, 9, 20), true);

        // when
        order.updateEstimatedAssemblyAt(java.time.LocalDate.of(2026, 9, 16), false);

        // then
        assertThat(order.getEstimatedAssemblyAt()).isEqualTo(java.time.LocalDate.of(2026, 9, 20));
        assertThat(order.getEstimatedShippingAt()).isEqualTo(java.time.LocalDate.of(2026, 9, 23));
    }

    @Test
    @DisplayName("a confirmation carrying no date still re-derives the shipping date from the assembly date")
    void reDerivesTheShippingDateWhenTheConfirmationCarriesNoDate() {
        // given: a dropship leg landed first and left both dates on the same day
        Order order = new Order("store-1");
        order.setOrderRealizationDays(3);
        order.updateEstimatedAssemblyAt(java.time.LocalDate.of(2026, 9, 14), true);

        // when: the leg that adds a warehouse stop is confirmed without a date of its own
        order.updateEstimatedAssemblyAt(null, false);

        // then
        assertThat(order.getEstimatedAssemblyAt()).isEqualTo(java.time.LocalDate.of(2026, 9, 14));
        assertThat(order.getEstimatedShippingAt()).isEqualTo(java.time.LocalDate.of(2026, 9, 17));
    }

    @Test
    @DisplayName("an order with no date at all is left alone by a confirmation carrying no date")
    void leavesAnOrderWithoutAnyDateAlone() {
        // given
        Order order = new Order("store-1");
        order.setOrderRealizationDays(3);

        // when
        order.updateEstimatedAssemblyAt(null, false);

        // then
        assertThat(order.getEstimatedAssemblyAt()).isNull();
        assertThat(order.getEstimatedShippingAt()).isNull();
    }

    @Test
    void hasShipmentToBookWhenSomeShipmentLacksLabelDataOrThereIsNone() {
        // given
        Order order = new Order("store-1");
        Shipment withData = new Shipment(ShipmentType.Courier);
        withData.setCarrier("DPD");
        withData.setTrackingNo("T-1");
        withData.setShippedAt(java.time.LocalDateTime.now());
        Shipment empty = new Shipment(ShipmentType.Courier);
        // when / then
        order.setShipments(List.of(withData));
        assertThat(order.hasShipmentToBook()).isFalse();
        order.setShipments(List.of(withData, empty));
        assertThat(order.hasShipmentToBook()).isTrue();
        // the only shipment removed (2026-09-30): the courier booking creates the shipment, so there is one to book
        order.setShipments(List.of());
        assertThat(order.hasShipmentToBook()).isTrue();
    }

    @Test
    void aShipmentWithACourierOrderIsNeverBookedAgainAndIsFoundForCancellationWithoutItsDate() {
        // given: a booked courier whose shipped date is gone (legacy data), next to a delivered courier order
        Order order = new Order("store-1");
        Shipment booked = new Shipment(ShipmentType.Courier);
        booked.setCarrier("DPD");
        booked.setTrackingNo("T-2");
        booked.setExternalId("EXT-2");
        Shipment delivered = new Shipment(ShipmentType.Courier);
        delivered.setCarrier("DPD");
        delivered.setTrackingNo("T-1");
        delivered.setExternalId("EXT-1");
        delivered.setShippedAt(java.time.LocalDateTime.of(2026, 9, 1, 9, 0));
        delivered.setDeliveredAt(java.time.LocalDateTime.of(2026, 9, 2, 9, 0));
        order.setShipments(List.of(delivered, booked));

        // when / then: booking again would pay for a second label; the delivered parcel has nothing to cancel
        assertThat(order.hasShipmentToBook()).isFalse();
        assertThat(order.firstShipmentWithShippingData()).contains(delivered);
        assertThat(order.courierShipmentToCancel()).contains(booked);
        order.setShipments(List.of(delivered));
        assertThat(order.courierShipmentToCancel()).isEmpty();
    }

    @Test
    void firstShipmentWithShippingDataSkipsEmptyRows() {
        // given
        Order order = new Order("store-1");
        Shipment empty = new Shipment(ShipmentType.Courier);
        Shipment withData = new Shipment(ShipmentType.Courier);
        withData.setCarrier("DPD");
        withData.setTrackingNo("T-1");
        withData.setShippedAt(java.time.LocalDateTime.now());
        order.setShipments(List.of(empty, withData));
        // then
        assertThat(order.firstShipmentWithShippingData()).contains(withData);
    }

    @Test
    void shippingAddressCanChangeInShippingWithoutALabel() {
        // given
        Order order = new Order("store-1");
        order.setStatus(OrderStatus.Shipping);
        order.setShipments(List.of(new Shipment(ShipmentType.PersonalCollection)));

        // then
        assertThat(order.canOperatorChangeShippingAddress()).isTrue();
    }

    @Test
    void shippingAddressIsLockedOnAClosedOrder() {
        // given
        Order completed = new Order("store-1");
        completed.setStatus(OrderStatus.Completed);
        Order cancelled = new Order("store-1");
        cancelled.setStatus(OrderStatus.Cancelled);

        // then
        assertThat(completed.canOperatorChangeShippingAddress()).isFalse();
        assertThat(cancelled.canOperatorChangeShippingAddress()).isFalse();
    }

    @Test
    void shippingAddressIsLockedByALabel() {
        // given
        Order order = new Order("store-1");
        order.setStatus(OrderStatus.Realization);
        Shipment labelled = new Shipment(ShipmentType.Courier);
        labelled.setTrackingNo("T-1");
        boolean before = order.canOperatorChangeShippingAddress();

        // when
        order.setShipments(List.of(labelled));

        // then
        assertThat(before).isTrue();
        assertThat(order.canOperatorChangeShippingAddress()).isFalse();
    }

    @Test
    void aLegacyOrderWithoutAddressesSplitsWithoutFailing() {
        // given
        Order order = new Order("s");
        order.setBillingDetails(null);
        order.setShippingDetails(null);

        // when
        Order split = order.createSplit();

        // then
        assertThat(split.getBillingDetails()).isNull();
        assertThat(split.getShippingDetails()).isNull();
    }

    @Test
    void anOrderWithoutShipmentsIsNotDelivered() {
        // given: allMatch on an empty list is true; the only shipment may have been removed
        Order order = new Order("s");
        order.setShipments(new ArrayList<>());

        // when / then
        assertThat(order.isDelivered()).isFalse();
    }

    @Test
    void anOrderIsDeliveredOnceEveryShipmentHasADeliveryDate() {
        // given
        Order order = new Order("s");
        Shipment first = new Shipment(ShipmentType.Courier);
        first.setDeliveredAt(LocalDateTime.of(2026, 9, 28, 10, 0));
        Shipment second = new Shipment(ShipmentType.Courier);
        order.setShipments(new ArrayList<>(List.of(first, second)));

        // when
        boolean partly = order.isDelivered();
        second.setDeliveredAt(LocalDateTime.of(2026, 9, 29, 0, 0));

        // then
        assertThat(partly).isFalse();
        assertThat(order.isDelivered()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"New", "Blocked", "Assembly", "Assembled", "Realization", "Shipping"})
    void anOrderWithoutShipmentsBeforeDeliveryHasSomethingLeftToDeliver(OrderStatus status) {
        // given: every order is created with a shipment waiting to go out, so an empty list before delivery means its
        // only shipment was removed, never that the goods reached the customer
        Order order = new Order("s");
        order.setShipments(new ArrayList<>());
        order.setStatus(status);

        // when
        boolean nothingLeft = order.hasNothingLeftToDeliver();

        // then: settling it on payment and invoice would complete an order that never shipped
        assertThat(nothingLeft).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"Delivered", "Completed"})
    void anOrderWithoutShipmentsInDeliveredHasNothingLeftToDeliver(OrderStatus status) {
        // given: a legacy order delivered without any shipment recorded
        Order order = new Order("s");
        order.setShipments(new ArrayList<>());
        order.setStatus(status);

        // when
        boolean nothingLeft = order.hasNothingLeftToDeliver();

        // then
        assertThat(nothingLeft).isTrue();
    }

    @Test
    void cancelBlockersNameExactlyWhatCanBeCancelledMisses() {
        // given
        OrderItem delivered = new OrderItem("o1", "CPU", "Ryzen", 1, 100, "SKU", false, 0);
        delivered.setStatus(FulfilmentStatus.Delivered);
        OrderItem returned = new OrderItem("o1", "CPU", "Ryzen", 1, 100, "SKU", false, 0);
        returned.setStatus(FulfilmentStatus.Returned);
        Order open = new Order("store-1");
        open.setStatus(OrderStatus.Assembly);
        Order paid = new Order("store-1");
        paid.setStatus(OrderStatus.Delivered);
        paid.addPayment(new Payment("REF", "Jan", PaymentSource.BankTransfer, 100, 0));
        Order settled = new Order("store-1");
        settled.setStatus(OrderStatus.Delivered);

        // when / then: the reason on the page is built from these, canBeCancelled is "none of them"
        assertThat(open.cancelBlockers(List.of(returned))).containsExactly(Order.CancelBlocker.NOT_DELIVERED);
        assertThat(paid.cancelBlockers(List.of(delivered))).containsExactlyInAnyOrder(
                Order.CancelBlocker.PRODUCTS_NOT_RETURNED, Order.CancelBlocker.PAYMENTS_NOT_REFUNDED);
        assertThat(paid.cancelBlockers(List.of(returned))).containsExactly(Order.CancelBlocker.PAYMENTS_NOT_REFUNDED);
        assertThat(settled.cancelBlockers(List.of(returned))).isEmpty();
        for (Order order : List.of(open, paid, settled)) {
            for (List<OrderItem> items : List.of(List.of(delivered), List.of(returned), List.<OrderItem>of())) {
                assertThat(order.canBeCancelled(items)).isEqualTo(order.cancelBlockers(items).isEmpty());
            }
        }
    }
}
