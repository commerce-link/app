package pl.commercelink.orders;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pl.commercelink.documents.Document;
import pl.commercelink.documents.DocumentType;

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
}
