package pl.commercelink.orders;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pl.commercelink.documents.Document;
import pl.commercelink.documents.DocumentType;

import static org.assertj.core.api.Assertions.assertThat;

class OrderTest {

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
}
