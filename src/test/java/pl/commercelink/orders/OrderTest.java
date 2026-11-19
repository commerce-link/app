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
import java.util.Set;
import java.util.UUID;

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
    @DisplayName("an RMA replacement counts as invoiced without a closing document, so it cannot be split")
    void rmaReplacementCountsAsInvoicedWithoutClosingDocument() {
        // given
        Order order = new Order("store-1");
        order.setStatus(OrderStatus.New);
        order.setSource(new OrderSource("RMA", OrderSourceType.Other));

        // when / then
        assertThat(order.isInvoiced()).isTrue();
        assertThat(order.hasClosingDocument()).isFalse();
        assertThat(order.canBeSplit()).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = DocumentType.class, names = {"InvoiceVat", "InvoiceFinal", "InvoicePersonal", "Receipt", "GoodsIssue"})
    @DisplayName("an invoice or a goods issue blocks the split of an order")
    void invoiceOrGoodsIssueBlocksSplit(DocumentType type) {
        // given
        Order order = new Order("store-1");
        order.setStatus(OrderStatus.New);
        order.addDocument(new Document("doc-1", "DOC/1", null, type));

        // when / then
        assertThat(order.canBeSplit()).isFalse();
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

    @Test
    void orderWhosePricesSumToADoubleJustOffThePaidAmountIsFullyPaid() {
        // given
        Order order = new Order("store-1");
        order.setTotalPrice(449.55 + 19.99 + 4.35);
        order.addPayment(Payment.bankTransfer("ref-1", "Jan", 473.89));

        // when / then
        assertThat(order.isFullyPaid()).isTrue();
    }

    @Test
    void orderPaidACentShortIsNotFullyPaid() {
        // given
        Order order = new Order("store-1");
        order.setTotalPrice(449.55 + 19.99 + 4.35);
        order.addPayment(Payment.bankTransfer("ref-1", "Jan", 473.88));

        // when / then
        assertThat(order.isFullyPaid()).isFalse();
    }

    @Test
    void orderOverpaidByACentIsNotFullyPaid() {
        // given
        Order order = new Order("store-1");
        order.setTotalPrice(449.55 + 19.99 + 4.35);
        order.addPayment(Payment.bankTransfer("ref-1", "Jan", 473.90));

        // when / then
        assertThat(order.isFullyPaid()).isFalse();
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
    void anOrderBeforeShippingIsCancelledWhateverItsItemsAndPayments() {
        // given
        OrderItem fresh = product(FulfilmentStatus.New);
        OrderItem reserved = reservedProduct();
        Order paid = new Order("store-1");
        paid.setStatus(OrderStatus.Assembled);
        paid.addPayment(new Payment("REF", "Jan", PaymentSource.BankTransfer, 100, 0));

        // when
        Set<Order.CancelBlocker> blockers = paid.cancelBlockers(List.of(fresh, reserved), Set.of());

        // then
        assertThat(blockers).isEmpty();
        assertThat(paid.canBeCancelled(List.of(fresh, reserved), Set.of())).isTrue();
    }

    @Test
    void anOrderBeforeShippingNamesWhatHasToBeReleasedByHandFirst() {
        // given
        OrderItem dropshipped = reservedProduct();
        dropshipped.setStatus(FulfilmentStatus.Ordered);
        OrderItem claimed = product(FulfilmentStatus.Allocation);
        claimed.setClaimedDeliveryId("delivery-being-created");
        OrderItem inClaim = product(FulfilmentStatus.InRMA);
        Order booked = new Order("store-1");
        booked.setStatus(OrderStatus.Realization);
        Shipment courier = new Shipment(ShipmentType.Courier);
        courier.setExternalId("courier-order-1");
        booked.setShipments(new ArrayList<>(List.of(courier)));
        Order open = new Order("store-1");
        open.setStatus(OrderStatus.Assembly);

        // when / then
        assertThat(open.cancelBlockers(List.of(dropshipped), Set.of(dropshipped.getItemId())))
                .containsExactly(Order.CancelBlocker.DROPSHIP_ITEMS);
        assertThat(open.cancelBlockers(List.of(claimed), Set.of())).containsExactly(Order.CancelBlocker.ITEMS_NOT_RELEASABLE);
        assertThat(open.cancelBlockers(List.of(inClaim), Set.of())).containsExactly(Order.CancelBlocker.ITEMS_NOT_RELEASABLE);
        assertThat(booked.cancelBlockers(List.of(), Set.of())).containsExactly(Order.CancelBlocker.COURIER_ORDERED);
    }

    @Test
    void onceTheGoodsWentOutCancellingWaitsForEveryProductToComeBack() {
        // given
        OrderItem delivered = product(FulfilmentStatus.Delivered);
        OrderItem returned = product(FulfilmentStatus.Returned);
        Order paid = new Order("store-1");
        paid.setStatus(OrderStatus.Delivered);
        paid.addPayment(new Payment("REF", "Jan", PaymentSource.BankTransfer, 100, 0));
        Order shippedParcel = new Order("store-1");
        shippedParcel.setStatus(OrderStatus.Realization);
        Shipment gone = new Shipment(ShipmentType.Courier);
        gone.setShippedAt(LocalDateTime.now());
        shippedParcel.setShipments(new ArrayList<>(List.of(gone)));

        // when / then: the money still on the order no longer stands in the way, the refund is recorded afterwards
        assertThat(paid.cancelBlockers(List.of(delivered), Set.of())).containsExactly(Order.CancelBlocker.PRODUCTS_NOT_RETURNED);
        assertThat(paid.cancelBlockers(List.of(returned), Set.of())).isEmpty();
        assertThat(shippedParcel.cancelBlockers(List.of(delivered), Set.of()))
                .containsExactly(Order.CancelBlocker.PRODUCTS_NOT_RETURNED);
    }

    @Test
    void aCancelledOrderIsNotCancelledAgain() {
        // given
        Order cancelled = new Order("store-1");
        cancelled.setStatus(OrderStatus.Cancelled);

        // when
        Set<Order.CancelBlocker> blockers = cancelled.cancelBlockers(List.of(product(FulfilmentStatus.Cancelled)), Set.of());

        // then
        assertThat(blockers).containsExactly(Order.CancelBlocker.ALREADY_CANCELLED);
    }

    @Test
    void cancellingBeforeShippingMarksEveryItemCancelledAndLeavesNothingToPay() {
        // given
        OrderItem fresh = product(FulfilmentStatus.New);
        fresh.setEan("5901234123457");
        OrderItem supplierAssigned = product(FulfilmentStatus.New);
        supplierAssigned.setDeliveryId("Acme");
        OrderItem waitingForSupplier = product(FulfilmentStatus.Allocation);
        waitingForSupplier.setDeliveryId("Acme");
        OrderItem service = new OrderItem("o1", "Dostawa", "Kurier", 1, 20, null, false, 0);
        service.setService(true);
        Order order = new Order("store-1");
        order.setStatus(OrderStatus.Assembly);
        order.setTotalPrice(220);
        order.addPayment(new Payment("REF", "Jan", PaymentSource.BankTransfer, 220, 0));

        // when
        order.cancel(List.of(fresh, supplierAssigned, waitingForSupplier, service));

        // then: the items and the order keep their value, nothing is left to pay, so the payment is owed back
        assertThat(List.of(fresh, supplierAssigned, waitingForSupplier, service)).allMatch(OrderItem::isCancelled);
        assertThat(fresh.getPrice()).isEqualTo(100);
        assertThat(service.getPrice()).isEqualTo(20);
        assertThat(fresh.getEan()).isEqualTo("5901234123457");
        assertThat(supplierAssigned.getDeliveryId()).isNull();
        assertThat(service.getDeliveryId()).isNull();
        assertThat(waitingForSupplier.getDeliveryId()).isNull();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.Cancelled);
        assertThat(order.getTotalPrice()).isEqualTo(220);
        assertThat(order.getAmountDue()).isZero();
        assertThat(order.getUnpaidAmount()).isEqualTo(-220);
    }

    @Test
    void cancellingRecordsWhenItHappened() {
        // given
        Order order = new Order("store-1");
        order.setStatus(OrderStatus.Assembly);
        LocalDateTime before = LocalDateTime.now();

        // when
        order.cancel(List.of(product(FulfilmentStatus.New)));

        // then
        assertThat(order.getCancelledAt()).isBetween(before, LocalDateTime.now());
    }

    @Test
    void finishingTheItemsOfACancelledOrderKeepsItsCancellationDate() {
        // given: cancelled a week ago, an item was left behind by a failed save
        LocalDateTime aWeekAgo = LocalDateTime.now().minusDays(7);
        Order order = new Order("store-1");
        order.setStatus(OrderStatus.Cancelled);
        order.setCancelledAt(aWeekAgo);

        // when
        order.cancel(List.of(product(FulfilmentStatus.New)));

        // then
        assertThat(order.getCancelledAt()).isEqualTo(aWeekAgo);
    }

    @Test
    void anOrderCancelledBeforeTheDateWasRecordedIsNotGivenOneAfterwards() {
        // given
        Order order = new Order("store-1");
        order.setStatus(OrderStatus.Cancelled);

        // when
        order.cancel(List.of(product(FulfilmentStatus.New)));

        // then
        assertThat(order.getCancelledAt()).isNull();
    }

    @Test
    void goodsTheWarehouseCouldNotTakeBackRefuseTheCancel() {
        // given: reserved, but without the manufacturer code the release goes by (older data)
        OrderItem reserved = reservedProduct();
        reserved.setManufacturerCode(null);
        Order open = new Order("store-1");
        open.setStatus(OrderStatus.Assembled);

        // when
        Set<Order.CancelBlocker> blockers = open.cancelBlockers(List.of(reserved), Set.of());

        // then
        assertThat(blockers).containsExactly(Order.CancelBlocker.ITEMS_NOT_RELEASABLE);
    }

    @Test
    void aCancelThatStoppedHalfWayIsFinishedByCancellingAgain() {
        // given: the order was saved as Cancelled and its items were not, or the other way round
        Order cancelled = new Order("store-1");
        cancelled.setStatus(OrderStatus.Cancelled);
        Order open = new Order("store-1");
        open.setStatus(OrderStatus.New);

        // when / then
        assertThat(cancelled.cancelBlockers(List.of(product(FulfilmentStatus.New)), Set.of())).isEmpty();
        assertThat(open.cancelBlockers(List.of(product(FulfilmentStatus.Cancelled)), Set.of())).isEmpty();
    }

    private static OrderItem reservedProduct() {
        OrderItem item = product(FulfilmentStatus.Delivered);
        item.setEan("5901234123457");
        item.setManufacturerCode("MFN-1");
        item.setDeliveryId("delivery-1");
        return item;
    }

    private static OrderItem product(FulfilmentStatus status) {
        OrderItem item = new OrderItem("o1", "CPU", "Ryzen", 1, 100, "SKU", false, 0);
        item.setItemId(UUID.randomUUID().toString());
        item.setStatus(status);
        return item;
    }

    @Test
    void paymentsRefundedInFullDoNotBlockTheSplitWhenTheirDoubleSumIsJustOffZero() {
        // given
        Order order = splittableOrder();
        paidAndRefundedInFull(order);

        // when / then
        assertThat(order.canBeSplit()).isTrue();
    }

    @Test
    void aCentLeftAfterTheRefundsBlocksTheSplit() {
        // given
        Order order = splittableOrder();
        order.addPayment(Payment.bankTransfer("REF-1", "Jan", 181.58));
        order.addPayment(new Payment("REF-2", "Jan", PaymentSource.BankTransfer, PaymentDirection.Outgoing, -181.57, 0, null, null));

        // when / then
        assertThat(order.canBeSplit()).isFalse();
    }

    private static void paidAndRefundedInFull(Order order) {
        order.addPayment(Payment.bankTransfer("REF-1", "Jan", 1703.14));
        order.addPayment(Payment.bankTransfer("REF-2", "Jan", 2642.55));
        order.addPayment(new Payment("REF-3", "Jan", PaymentSource.BankTransfer, PaymentDirection.Outgoing, -4345.69, 0, null, null));
    }

    private static Payment pending(PaymentSource source, Double expectedAmount) {
        Payment payment = new Payment(source);
        payment.setExpectedAmount(expectedAmount);
        return payment;
    }

    @Test
    void moneyGoesToThePendingPaymentOfItsMethodElseToTheRest() {
        // given: 20 000 zł, 5 000 zł by transfer and the rest in installments
        Order order = new Order("store-1");
        order.setTotalPrice(20000);
        Payment transfer = pending(PaymentSource.BankTransfer, 5000.0);
        Payment installments = pending(PaymentSource.Installments, null);
        order.addPayment(transfer);
        order.addPayment(installments);

        // when / then
        assertThat(order.pendingPaymentFor(PaymentSource.BankTransfer)).isSameAs(transfer);
        assertThat(order.pendingPaymentFor(PaymentSource.Installments)).isSameAs(installments);
        assertThat(order.pendingPaymentFor(PaymentSource.Card)).isSameAs(installments);
        assertThat(order.pendingPaymentFor(null)).isSameAs(installments);
    }

    @Test
    void anOwnAmountOfTheMethodComesBeforeTheRestOfTheSameMethod() {
        // given: the rest waits by transfer, and so do 5 000 zł added later
        Order order = new Order("store-1");
        order.setTotalPrice(20000);
        Payment rest = pending(PaymentSource.BankTransfer, null);
        Payment own = pending(PaymentSource.BankTransfer, 5000.0);
        order.addPayment(rest);
        order.addPayment(own);

        // when / then
        assertThat(order.pendingPaymentFor(PaymentSource.BankTransfer)).isSameAs(own);
    }

    @Test
    void moneyOfAMethodNothingWaitsForIsAPaymentOfItsOwnWhenNoPaymentWaitsForTheRest() {
        // given
        Order order = new Order("store-1");
        order.setTotalPrice(20000);
        Payment transfer = pending(PaymentSource.BankTransfer, 5000.0);
        order.addPayment(transfer);
        order.addPayment(pending(PaymentSource.Installments, 15000.0));

        // when / then
        assertThat(order.pendingPaymentFor(PaymentSource.Card)).isNull();
        assertThat(order.pendingPaymentFor(null)).isSameAs(transfer);
    }

    @Test
    void theRestIsWhatIsUnpaidBeyondTheOwnAmountsOfThePendingPayments() {
        // given: 20 000 zł, 2 000 zł paid, 5 000 zł waited for by transfer
        Order order = new Order("store-1");
        order.setTotalPrice(20000);
        order.addPayment(Payment.bankTransfer("REF-1", "Jan", 2000));
        Payment transfer = pending(PaymentSource.BankTransfer, 5000.0);
        Payment installments = pending(PaymentSource.Installments, null);
        order.addPayment(transfer);
        order.addPayment(installments);

        // when / then
        assertThat(order.expectedAmountOf(installments)).isEqualTo(13000.0);
        assertThat(order.expectedAmountOf(transfer)).isEqualTo(5000.0);
        assertThat(order.getPaidAmount()).isEqualTo(2000.0);
    }

    @Test
    void theRestIsNeverBelowZero() {
        // given: the own amounts are above what is unpaid
        Order order = new Order("store-1");
        order.setTotalPrice(1000);
        order.addPayment(pending(PaymentSource.BankTransfer, 1500.0));
        Payment rest = pending(PaymentSource.Installments, null);
        order.addPayment(rest);

        // when / then
        assertThat(order.expectedAmountOf(rest)).isEqualTo(0.0);
    }

    @Test
    void aPendingPaymentWithItsOwnAmountPaidInPartKeepsWaitingForTheDifference() {
        // given
        Order order = new Order("store-1");
        order.setTotalPrice(20000);
        Payment transfer = pending(PaymentSource.BankTransfer, 5000.0);
        transfer.setName("Jan Kowalski");
        transfer.setReferenceNo("PF/1/2026");
        Payment installments = pending(PaymentSource.Installments, null);
        order.addPayment(transfer);
        order.addPayment(installments);

        // when
        order.fill(transfer, 3000);

        // then
        assertThat(transfer.getAmount()).isEqualTo(3000.0);
        assertThat(transfer.getExpectedAmount()).isNull();
        assertThat(order.getPayments()).hasSize(3);
        Payment difference = order.getPayments().get(2);
        assertThat(difference.isUnsettled()).isTrue();
        assertThat(difference.getSource()).isEqualTo(PaymentSource.BankTransfer);
        assertThat(difference.getExpectedAmount()).isEqualTo(2000.0);
        assertThat(difference.getName()).isEqualTo("Jan Kowalski");
        assertThat(difference.getReferenceNo()).isEqualTo("PF/1/2026");
        assertThat(order.expectedAmountOf(installments)).isEqualTo(15000.0);
    }

    @Test
    void aPendingPaymentPaidInFullOrAboveLeavesNothingWaitingAndTheSurplusLowersTheRest() {
        // given
        Order order = new Order("store-1");
        order.setTotalPrice(20000);
        Payment transfer = pending(PaymentSource.BankTransfer, 5000.0);
        Payment installments = pending(PaymentSource.Installments, null);
        order.addPayment(transfer);
        order.addPayment(installments);

        // when
        order.fill(transfer, 5500);

        // then
        assertThat(order.getPayments()).hasSize(2);
        assertThat(order.getPendingPayments()).containsExactly(installments);
        assertThat(order.expectedAmountOf(installments)).isEqualTo(14500.0);
    }

    @Test
    void theRestPaidInPartKeepsWaitingForWhatIsLeftWithItsMethod() {
        // given
        Order order = new Order("store-1");
        order.setTotalPrice(20000);
        Payment rest = pending(PaymentSource.Installments, null);
        rest.setName("Santander");
        order.addPayment(rest);

        // when
        order.fill(rest, 19990);

        // then
        assertThat(rest.getAmount()).isEqualTo(19990.0);
        Payment left = order.getRestPayment();
        assertThat(left).isNotSameAs(rest);
        assertThat(left.getSource()).isEqualTo(PaymentSource.Installments);
        assertThat(left.getName()).isEqualTo("Santander");
        assertThat(order.getRestAmount()).isEqualTo(10.0);
    }

    @Test
    void theRestPaidInFullGoesAndSoDoesTheRestNothingIsLeftOf() {
        // given: 20 000 zł, 5 000 zł by transfer and the rest in instalments
        Order order = new Order("store-1");
        order.setTotalPrice(20000);
        Payment transfer = pending(PaymentSource.BankTransfer, 5000.0);
        Payment rest = pending(PaymentSource.Installments, null);
        order.addPayment(transfer);
        order.addPayment(rest);
        Payment paidOnly = pending(PaymentSource.BankTransfer, null);
        Order paidByTransfer = new Order("store-1");
        paidByTransfer.setTotalPrice(100);
        paidByTransfer.addPayment(paidOnly);

        // when
        order.fill(rest, 15000);
        order.fill(transfer, 6000);
        paidByTransfer.fill(paidOnly, 100);

        // then
        assertThat(order.getPendingPayments()).isEmpty();
        assertThat(order.getPayments()).containsExactly(transfer, rest);
        assertThat(paidByTransfer.getPayments()).containsExactly(paidOnly);
    }

    @Test
    void keepRestAddsATransferForWhatThePaymentsWithTheirOwnAmountsLeave() {
        // given
        Order order = new Order("store-1");
        order.setTotalPrice(20000);
        Payment installments = pending(PaymentSource.Installments, 15000.0);
        order.addPayment(installments);

        // when
        order.keepRest();

        // then
        Payment rest = order.getRestPayment();
        assertThat(rest.getSource()).isEqualTo(PaymentSource.BankTransfer);
        assertThat(order.getRestAmount()).isEqualTo(5000.0);
    }

    @Test
    void theOnlyPendingPaymentOfAnOrderNothingWasPaidForStays() {
        // given: an order of 0 zł (all of it free) still says how the customer pays
        Order order = new Order("store-1");
        Payment only = pending(PaymentSource.Card, null);
        order.addPayment(only);

        // when
        order.keepRest();

        // then
        assertThat(order.getPayments()).containsExactly(only);
    }

    @Test
    void replacingAPendingPaymentWithMoneyKeepsWhatItStillWaitsForWaiting() {
        // given
        Order order = new Order("store-1");
        order.setTotalPrice(20000);
        Payment transfer = pending(PaymentSource.BankTransfer, 5000.0);
        transfer.setReferenceNo("PF/1");
        order.addPayment(transfer);
        order.addPayment(pending(PaymentSource.Installments, null));

        // when
        order.replacePayment(0, Payment.bankTransfer("PF/1", "Jan", 3000));

        // then
        assertThat(order.getPayments()).extracting(Payment::getAmount).containsExactly(3000.0, 0.0, 0.0);
        assertThat(order.getPayments().get(2).getExpectedAmount()).isEqualTo(2000.0);
        assertThat(order.getPayments().get(2).getReferenceNo()).isEqualTo("PF/1");
        assertThat(order.getRestAmount()).isEqualTo(15000.0);
    }

    @Test
    void splitPutsThePartsAndALastPartForWhatTheyLeaveInPlaceOfThePendingPayment() {
        // given
        Order order = new Order("store-1");
        order.setTotalPrice(20000);
        order.addPayment(Payment.bankTransfer("R", "Jan", 1000));
        Payment transfer = pending(PaymentSource.BankTransfer, 9000.0);
        transfer.setReferenceNo("PF/2");
        order.addPayment(transfer);
        order.addPayment(pending(PaymentSource.Installments, null));

        // when
        order.split(transfer, new ArrayList<>(List.of(pending(PaymentSource.Card, 2000.0),
                pending(PaymentSource.CashOnDelivery, 3000.0))), PaymentSource.BankTransfer);

        // then
        assertThat(order.getPayments()).extracting(Payment::getSource).containsExactly(PaymentSource.BankTransfer,
                PaymentSource.Card, PaymentSource.CashOnDelivery, PaymentSource.BankTransfer, PaymentSource.Installments);
        assertThat(order.getPayments()).extracting(Payment::getExpectedAmount)
                .containsExactly(null, 2000.0, 3000.0, 4000.0, null);
        assertThat(order.getPayments().get(1).getReferenceNo()).isEqualTo("PF/2");
        assertThat(order.getRestAmount()).isEqualTo(10000.0);
    }
}
