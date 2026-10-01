package pl.commercelink.web.deliveries.details;

import org.junit.jupiter.api.Test;
import pl.commercelink.documents.Document;
import pl.commercelink.documents.DocumentType;
import pl.commercelink.inventory.deliveries.Delivery;
import pl.commercelink.inventory.deliveries.DeliveryOrderStatus;
import pl.commercelink.inventory.deliveries.DeliveryTrackingState;
import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.Payment;
import pl.commercelink.orders.PaymentDirection;
import pl.commercelink.orders.PaymentSource;
import pl.commercelink.orders.event.Event;
import pl.commercelink.orders.event.EventType;
import pl.commercelink.stores.ConnectionMode;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.commercelink.web.deliveries.details.DeliveryFixtures.*;

class DeliveryCardsFactoryTest {

    private static DeliveryLinks links(DeliveryViewer viewer, Delivery delivery) {
        return DeliveryLinks.of(viewer.superAdmin(), STORE_ID, delivery.getDeliveryId());
    }

    @Test
    void documentsLinkOnlyWebAddressesAndTheStoreAdminManagesInvoices() {
        // given
        Delivery delivery = received(withGoodsReceipt(warehouse()));
        delivery.addDocument(new Document("inv-1", "FV/ACME/0412", "https://invoices.example/0412", DocumentType.InvoiceVat, LocalDate.of(2026, 9, 25)));
        delivery.addDocument(new Document("inv-2", "FV/EVIL", "javascript:alert(1)", DocumentType.InvoiceVat, null));

        // when
        DeliveryPageModel.DocumentsCard admin = DeliveryCardsFactory.documents(delivery, ADMIN, links(ADMIN, delivery));
        DeliveryPageModel.DocumentsCard superAdmin = DeliveryCardsFactory.documents(delivery, SUPER_ADMIN, links(SUPER_ADMIN, delivery));

        // then
        assertThat(admin.rows()).extracting(DeliveryPageModel.DocumentRow::href).containsExactly(
                "/dashboard/warehouse-documents/details?documentId=pz-1", "https://invoices.example/0412", null);
        assertThat(admin.rows().get(0).typeKey()).isEqualTo("DocumentType.GoodsReceipt");
        assertThat(admin.rows().get(0).issuedAt()).isEqualTo("01.10.2026");
        assertThat(admin.rows().get(1).syncHref()).isEqualTo("/dashboard/deliveries/sync/preview?deliveryId=" + DELIVERY_ID + "&invoiceId=inv-1");
        assertThat(admin.rows().get(1).unlinkHref()).isEqualTo("/dashboard/deliveries/" + DELIVERY_ID + "/confirm/unlink-invoice?invoiceId=inv-1");
        assertThat(admin.link().visible()).isTrue();
        assertThat(superAdmin.rows().get(0).href()).isEqualTo("/dashboard/store/store-1/warehouse-documents/details?documentId=pz-1");
        assertThat(superAdmin.rows().get(1).syncHref()).isNull();
        assertThat(superAdmin.link().visible()).isFalse();
    }

    @Test
    void theInvoicePillTurnsAmberOnceTheGoodsAreIn() {
        // when
        DeliveryPageModel.DocumentsCard inTransit = DeliveryCardsFactory.documents(warehouse(), ADMIN, links(ADMIN, warehouse()));
        DeliveryPageModel.DocumentsCard arrived = DeliveryCardsFactory.documents(received(warehouse()), ADMIN, links(ADMIN, warehouse()));
        Delivery internal = warehouse();
        internal.setProvider(SupplierRegistry.WAREHOUSE);

        // then
        assertThat(inTransit.invoiceTone()).isEqualTo("is-neutral");
        assertThat(inTransit.emptyKey()).isEqualTo("deliveries.details.documents.empty.warehouse");
        assertThat(arrived.invoiceTone()).isEqualTo("is-warn");
        assertThat(arrived.emptyKey()).isEqualTo("deliveries.details.documents.empty.link");
        assertThat(DeliveryCardsFactory.documents(internal, ADMIN, links(ADMIN, internal)).showInvoicePills()).isFalse();
        assertThat(DeliveryCardsFactory.documents(warehouse(), USER, links(USER, warehouse())).emptyKey())
                .isEqualTo("deliveries.details.documents.empty.warehouse.readOnly");
    }

    @Test
    void paymentsSummariseWhatIsOwedAndWhenAndOnlyTheStoreAdminEditsThem() {
        // given
        Delivery delivery = warehouse();
        delivery.addPayment(new Payment("MH-2026/0917", "mBank", PaymentSource.BankTransfer, PaymentDirection.Outgoing,
                3000, 0, "202610010417", LocalDate.of(2026, 10, 1)));

        // when
        DeliveryPageModel.PaymentsCard admin = DeliveryCardsFactory.payments(delivery, ADMIN, links(ADMIN, delivery));
        DeliveryPageModel.PaymentsCard superAdmin = DeliveryCardsFactory.payments(delivery, SUPER_ADMIN, links(SUPER_ADMIN, delivery));

        // then
        assertThat(admin.pillKey()).isEqualTo("deliveries.details.payments.underpaid");
        assertThat(admin.pillTone()).isEqualTo("is-warn");
        assertThat(admin.pillAmount()).isEqualTo("3 253,32");
        assertThat(admin.toPay()).isEqualTo("6 253,32");
        assertThat(admin.paid()).isEqualTo("3 000,00");
        assertThat(admin.remaining()).isEqualTo("3 253,32");
        assertThat(admin.remainingDue()).isTrue();
        assertThat(admin.dueDate()).isEqualTo("15.10.2026");
        assertThat(admin.paymentTerms()).isEqualTo(14);
        assertThat(admin.editable()).isTrue();
        DeliveryPageModel.PaymentRow row = admin.rows().get(0);
        assertThat(row.number()).isEqualTo(1);
        assertThat(row.amount()).isEqualTo("3 000,00");
        assertThat(row.sourceKey()).isEqualTo("PaymentSource.BankTransfer");
        assertThat(row.refund()).isFalse();
        assertThat(row.details()).extracting(DeliveryPageModel.PaymentDetail::dateArg).contains("01.10.2026");
        assertThat(row.dialogId()).isEqualTo("payment-0-dialog");
        assertThat(row.editHref()).endsWith("&open=payment-0#payment-0-dialog");
        assertThat(admin.fields().get(0)).isEqualTo(new DeliveryPageModel.PaymentFields("BankTransfer", "Outgoing", "mBank",
                "3000.00", "0.00", "MH-2026/0917", "202610010417", "2026-10-01"));
        assertThat(superAdmin.editable()).isFalse();
        assertThat(superAdmin.rows().get(0).editHref()).isNull();
    }

    @Test
    void anUnsetVatLeavesWhatIsOwedEmptyButKeepsTheSettlementPill() {
        // given
        Delivery unset = warehouse();
        unset.setTax(0.0);

        // when
        DeliveryPageModel.PaymentsCard card = DeliveryCardsFactory.payments(unset, ADMIN, links(ADMIN, unset));

        // then
        assertThat(card.toPay()).isNull();
        assertThat(card.remaining()).isNull();
        assertThat(card.pillKey()).isEqualTo("deliveries.details.payments.unpaid");
        assertThat(card.paid()).isEqualTo("0,00");
    }

    @Test
    void anIncomingPaymentIsARefund() {
        // given
        Delivery delivery = warehouse();
        delivery.addPayment(new Payment("R-1", "Acme", PaymentSource.BankTransfer, PaymentDirection.Incoming, 100, 0, null, null));

        // when / then
        assertThat(DeliveryCardsFactory.payments(delivery, ADMIN, links(ADMIN, delivery)).rows().get(0).refund()).isTrue();
    }

    @Test
    void historyIsNewestFirstWithAPolishLabelForEveryKnownEvent() {
        // given
        Delivery delivery = received(dropship());
        delivery.addEvent(new Event(EventType.action, "DELIVERY_UPDATED", LocalDateTime.of(2026, 10, 1, 16, 0)));
        delivery.addEvent(new Event(EventType.action, "DELIVERY_RECEIVED", LocalDateTime.of(2026, 10, 2, 9, 30)));
        delivery.addEvent(new Event(EventType.action, "SOMETHING_NEW", LocalDateTime.of(2026, 10, 2, 10, 0)));

        // when
        DeliveryPageModel.HistoryCard history = DeliveryCardsFactory.history(delivery);

        // then
        assertThat(history.events()).extracting(DeliveryPageModel.EventRow::labelKey).containsExactly(
                "deliveries.history.event.other", "deliveries.history.event.DELIVERY_RECEIVED.dropship",
                "deliveries.history.event.DELIVERY_UPDATED");
        assertThat(history.events().get(0).at()).isEqualTo("02.10.2026, 10:00");
        assertThat(history.rest()).isEmpty();
    }

    @Test
    void theConnectionRowSaysHowTheDeliveryWasOrdered() {
        // given
        Delivery integration = own(warehouse());
        Delivery platform = global(warehouse());
        Delivery outside = warehouse();
        Delivery confirmedByHand = own(warehouse());
        confirmedByHand.addEvent(new Event(EventType.action, "DELIVERY_ORDERED_MANUALLY", LocalDateTime.of(2026, 10, 1, 16, 0)));
        Delivery ownOutside = warehouse();
        ownOutside.setConnectionMode(ConnectionMode.OWN);

        // when / then
        assertThat(DeliveryCardsFactory.supplier(data(integration)).connectionKey()).isEqualTo("deliveries.details.supplier.connection.own");
        assertThat(DeliveryCardsFactory.supplier(data(platform)).connectionKey()).isEqualTo("deliveries.details.supplier.connection.global");
        assertThat(DeliveryCardsFactory.supplier(data(outside)).connectionKey()).isEqualTo("deliveries.details.supplier.connection.manual");
        assertThat(DeliveryCardsFactory.supplier(data(confirmedByHand)).connectionKey()).isEqualTo("deliveries.details.supplier.connection.manual");
        assertThat(DeliveryCardsFactory.supplier(data(ownOutside)).connectionKey()).isEqualTo("deliveries.details.supplier.connection.manual");
    }

    @Test
    void aLongSupplierNumberGetsItsOwnWrappedRowAndAPartnerLinkOnlyWhenItIsAWebAddress() {
        // given
        Delivery delivery = warehouse();
        delivery.setExternalDeliveryId("ACMEB-PO-3f2a9c1e-7b4d-4e8a-9c21-5d6f0a1b2c3d");
        delivery.setCounterpartyShortcut("ACME-HURT");
        DeliveryPageData linked = new DeliveryPageData(delivery, "AcmeB", "https://partner.example/po/1", List.of(), null,
                List.of(), null, Set.of(), null, null, NOW);
        DeliveryPageData script = new DeliveryPageData(delivery, "AcmeB", "javascript:alert(1)", List.of(), null,
                List.of(), null, Set.of(), null, null, NOW);

        // when
        DeliveryPageModel.SupplierCard card = DeliveryCardsFactory.supplier(linked);

        // then
        assertThat(card.longNumber()).isTrue();
        assertThat(card.externalIdHref()).isEqualTo("https://partner.example/po/1");
        assertThat(card.counterparty()).isEqualTo("ACME-HURT");
        assertThat(DeliveryCardsFactory.supplier(script).externalIdHref()).isNull();
    }

    @Test
    void theConsigneeCardShowsTheCustomerAndAfterShippingTheParcel() {
        // given
        Order order = dropshipOrder();
        order.firstShipment().orElseThrow().setTrackingNo("0000123456789W");
        order.firstShipment().orElseThrow().setShippedAt(LocalDateTime.of(2026, 10, 1, 0, 0));
        DeliveryPageData waiting = new DeliveryPageData(dropship(), "AcmeB", null, List.of(), order, List.of("DPD"), null,
                Set.of(), null, null, NOW);
        DeliveryPageData shipped = new DeliveryPageData(received(dropship()), "AcmeB", null, List.of(), order, List.of("DPD"),
                null, Set.of(), null, null, NOW);
        DeliveryPageData unknownOrder = data(dropship());

        // when
        DeliveryPageModel.ConsigneeCard before = DeliveryCardsFactory.consignee(waiting, links(ADMIN, dropship()));
        DeliveryPageModel.ConsigneeCard after = DeliveryCardsFactory.consignee(shipped, links(ADMIN, dropship()));

        // then
        assertThat(before.name()).isEqualTo("Barbara Zając");
        assertThat(before.cityLine()).isEqualTo("30-001 Kraków, PL");
        assertThat(before.orderShortId()).isEqualTo("1de57483");
        assertThat(before.orderHref()).isEqualTo("/dashboard/orders/1de57483-1111-2222-3333-444455556666");
        assertThat(before.shipmentTypeKey()).isEqualTo("ShipmentType.PickupPoint");
        assertThat(before.collectionPoint()).isEqualTo("PL12345");
        assertThat(before.trackingNo()).isNull();
        assertThat(before.supplierStateKey()).isEqualTo("deliveries.dropship.tracking.state.PENDING");
        assertThat(after.trackingNo()).isEqualTo("0000123456789W");
        assertThat(after.shippedAt()).isEqualTo("01.10.2026");
        assertThat(after.supplierStateKey()).isNull();
        assertThat(DeliveryCardsFactory.consignee(unknownOrder, links(ADMIN, dropship())).name()).isNull();
        assertThat(DeliveryCardsFactory.consignee(data(warehouse()), links(ADMIN, warehouse()))).isNull();
        assertThat(DeliveryCardsFactory.consignee(data(tracking(dropship(), DeliveryTrackingState.GIVEN_UP)), links(ADMIN, dropship()))
                .supplierStateKey()).isNull();
    }

    @Test
    void termsShowVatAsAPercentageAndOnlyEditorsMayChangeThem() {
        // given
        Delivery reverse = warehouse();
        reverse.setTax(1.0);

        // when
        DeliveryPageModel.TermsCard admin = DeliveryCardsFactory.terms(warehouse(), ADMIN, links(ADMIN, warehouse()), 5084);
        DeliveryPageModel.TermsCard user = DeliveryCardsFactory.terms(warehouse(), USER, links(USER, warehouse()), 5084);

        // then
        assertThat(admin.vatPercent()).isEqualTo("23");
        assertThat(admin.reverseCharge()).isFalse();
        assertThat(admin.goodsNet()).isEqualTo("5 084,00");
        assertThat(admin.totalNet()).isEqualTo("5 084,00");
        assertThat(admin.totalGross()).isEqualTo("6 253,32");
        assertThat(admin.estimatedDeliveryAt()).isEqualTo("08.10.2026");
        assertThat(admin.receivedAt()).isNull();
        assertThat(admin.edit().visible()).isTrue();
        assertThat(admin.editHref()).endsWith("&open=terms#terms-dialog");
        assertThat(user.edit().visible()).isFalse();
        assertThat(DeliveryCardsFactory.terms(reverse, ADMIN, links(ADMIN, reverse), 0).reverseCharge()).isTrue();
        assertThat(DeliveryCardsFactory.terms(global(withStatus(warehouse(), DeliveryOrderStatus.AWAITING_APPROVAL)), ADMIN,
                links(ADMIN, warehouse()), 0).edit().visible()).isFalse();
    }

    @Test
    void anUnsetVatShowsNeitherAPercentageNorAGrossTotal() {
        // given
        Delivery unset = warehouse();
        unset.setTax(0.0);

        // when
        DeliveryPageModel.TermsCard terms = DeliveryCardsFactory.terms(unset, ADMIN, links(ADMIN, unset), 5084);

        // then
        assertThat(terms.vatPercent()).isNull();
        assertThat(terms.reverseCharge()).isFalse();
        assertThat(terms.totalNet()).isEqualTo("5 084,00");
        assertThat(terms.totalGross()).isNull();
    }

    @Test
    void aVatOfExactlyOneIsTheReverseChargeAtZeroPercent() {
        // given
        Delivery reverse = warehouse();
        reverse.setTax(1.0);

        // when
        DeliveryPageModel.TermsCard terms = DeliveryCardsFactory.terms(reverse, ADMIN, links(ADMIN, reverse), 5084);

        // then
        assertThat(terms.reverseCharge()).isTrue();
        assertThat(terms.vatPercent()).isEqualTo("0");
        assertThat(terms.totalGross()).isEqualTo("5 084,00");
    }

    @Test
    void aStandardVatIsAPercentageWithAGrossTotal() {
        // when
        DeliveryPageModel.TermsCard terms = DeliveryCardsFactory.terms(warehouse(), ADMIN, links(ADMIN, warehouse()), 5084);

        // then
        assertThat(terms.reverseCharge()).isFalse();
        assertThat(terms.vatPercent()).isEqualTo("23");
        assertThat(terms.totalGross()).isEqualTo("6 253,32");
    }

    @Test
    void aPaymentDescribesOnlyThePartsItHasWithTheOperationDateNextToItsNumber() {
        // given
        Delivery delivery = warehouse();
        delivery.addPayment(new Payment("MH-2026/0917", "mBank", PaymentSource.BankTransfer, PaymentDirection.Outgoing,
                3000, 5, "202610010417", LocalDate.of(2026, 10, 1)));
        delivery.addPayment(new Payment("REF-2", " ", PaymentSource.Cash, PaymentDirection.Outgoing,
                100, 0, null, LocalDate.of(2026, 10, 2)));
        delivery.addPayment(new Payment(null, null, PaymentSource.Card, 50, 0));

        // when
        List<DeliveryPageModel.PaymentRow> rows = DeliveryCardsFactory.payments(delivery, ADMIN, links(ADMIN, delivery)).rows();

        // then
        assertThat(rows.get(0).details()).containsExactly(
                DeliveryPageModel.PaymentDetail.text("mBank"),
                DeliveryPageModel.PaymentDetail.message("deliveries.details.payments.reference", "MH-2026/0917"),
                new DeliveryPageModel.PaymentDetail(null, "deliveries.details.payments.operation", "202610010417", false, "01.10.2026"),
                new DeliveryPageModel.PaymentDetail(null, "deliveries.details.payments.fee", "5,00", true, null));
        assertThat(rows.get(1).details()).containsExactly(
                DeliveryPageModel.PaymentDetail.message("deliveries.details.payments.reference", "REF-2"),
                DeliveryPageModel.PaymentDetail.message("deliveries.details.payments.operationDate", "02.10.2026"));
        assertThat(rows.get(2).details()).isEmpty();
    }

    @Test
    void aBareDeliveryWithTheUnsetVatOfAPurchaseBuildsWithEmptyGrossAmounts() {
        // given
        Delivery bare = new Delivery();
        bare.setStoreId(STORE_ID);
        bare.setDeliveryId("d-bare");
        bare.setProvider("Other");
        bare.setTax(0.0);

        // when
        DeliveryPageModel model = DeliveryPageModelFactory.build(data(bare), ADMIN);

        // then
        assertThat(model.header().totalGross()).isNull();
        assertThat(model.terms().totalGross()).isNull();
        assertThat(model.terms().vatPercent()).isNull();
        assertThat(model.payments().toPay()).isNull();
        assertThat(model.payments().remaining()).isNull();
        assertThat(model.items().products()).isEmpty();
        assertThat(model.consignee()).isNull();
    }

    @Test
    void aDeliveryWithoutDatesNumbersOrItemsBuildsWithoutNulls() {
        // given
        Delivery bare = new Delivery();
        bare.setStoreId(STORE_ID);
        bare.setDeliveryId("d-bare");
        bare.setProvider("Other");
        bare.setTax(1.0);

        // when
        DeliveryPageModel model = DeliveryPageModelFactory.build(data(bare), ADMIN);

        // then
        assertThat(model.header().orderedAt()).isNull();
        assertThat(model.header().externalId()).isNull();
        assertThat(model.items().products()).isEmpty();
        assertThat(model.terms().estimatedDeliveryAt()).isNull();
        assertThat(model.payments().dueDate()).isNull();
        assertThat(model.history().events()).isEmpty();
        assertThat(model.comment().text()).isNull();
        assertThat(model.consignee()).isNull();
    }
}
