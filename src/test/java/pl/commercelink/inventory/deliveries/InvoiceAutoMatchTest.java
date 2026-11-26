package pl.commercelink.inventory.deliveries;

import org.junit.jupiter.api.Test;
import pl.commercelink.invoicing.api.Invoice;
import pl.commercelink.invoicing.api.Price;
import pl.commercelink.orders.Payment;
import pl.commercelink.web.dtos.InvoiceSyncPreview;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InvoiceAutoMatchTest {

    @Test
    void paymentsAgreeWhenBothSidesArePaidInFullOrNeitherHoldsAPayment() {
        // given
        Delivery unpaid = delivery();
        Delivery paid = delivery();
        paid.addPayment(Payment.outgoingBankTransfer("FV/1", null, 123.0));

        // when / then
        assertThat(InvoiceAutoMatch.paymentsAgree(invoice(false, false), unpaid)).isTrue();
        assertThat(InvoiceAutoMatch.paymentsAgree(invoice(true, false), paid)).isTrue();
    }

    @Test
    void paymentsDisagreeWhenOnlyOneSideIsPaidOrEitherIsPaidInPart() {
        // given
        Delivery unpaid = delivery();
        Delivery paid = delivery();
        paid.addPayment(Payment.outgoingBankTransfer("FV/1", null, 123.0));
        Delivery paidInPart = delivery();
        paidInPart.addPayment(Payment.outgoingBankTransfer("FV/1", null, 23.0));

        // when / then
        assertThat(InvoiceAutoMatch.paymentsAgree(invoice(true, false), unpaid)).isFalse();
        assertThat(InvoiceAutoMatch.paymentsAgree(invoice(false, false), paid)).isFalse();
        assertThat(InvoiceAutoMatch.paymentsAgree(invoice(false, true), unpaid)).isFalse();
        assertThat(InvoiceAutoMatch.paymentsAgree(invoice(true, false), paidInPart)).isFalse();
        assertThat(InvoiceAutoMatch.paymentsAgree(invoice(false, false), paidInPart)).isFalse();
    }

    @Test
    void everyProductOnItsOwnLineAtTheSamePriceIsPerfect() {
        // given
        InvoiceSyncPreview preview = preview(List.of(mapping("pos-1", 50.0), mapping("pos-2", 20.0)),
                List.of(option("pos-1", 50.0, 2), option("pos-2", 20.0, 1)));

        // when / then
        assertThat(InvoiceAutoMatch.isPerfect(preview)).isTrue();
    }

    @Test
    void shippingCostOnItsOwnLineIsPerfect() {
        // given
        InvoiceSyncPreview preview = preview(List.of(mapping("pos-1", 50.0)),
                List.of(option("pos-1", 50.0, 2), option("pos-ship", 15.0, 1)));
        preview.setShippingCost(15.0);
        preview.setShippingCostPositionId("pos-ship");

        // when / then
        assertThat(InvoiceAutoMatch.isPerfect(preview)).isTrue();
    }

    @Test
    void oneCentOfDifferenceIsNotPerfect() {
        // given
        InvoiceSyncPreview preview = preview(List.of(mapping("pos-1", 50.0)), List.of(option("pos-1", 50.01, 2)));

        // when / then
        assertThat(InvoiceAutoMatch.isPerfect(preview)).isFalse();
    }

    @Test
    void productWithoutAnInvoiceLineIsNotPerfect() {
        // given
        InvoiceSyncPreview preview = preview(List.of(mapping("pos-1", 50.0), mapping(null, 20.0)),
                List.of(option("pos-1", 50.0, 2)));

        // when / then
        assertThat(InvoiceAutoMatch.isPerfect(preview)).isFalse();
    }

    @Test
    void invoiceLineLeftOverIsNotPerfect() {
        // given
        InvoiceSyncPreview preview = preview(List.of(mapping("pos-1", 50.0)),
                List.of(option("pos-1", 50.0, 2), option("pos-extra", 9.0, 1)));

        // when / then
        assertThat(InvoiceAutoMatch.isPerfect(preview)).isFalse();
    }

    @Test
    void shippingCostWithoutAnInvoiceLineIsNotPerfect() {
        // given
        InvoiceSyncPreview preview = preview(List.of(mapping("pos-1", 50.0)), List.of(option("pos-1", 50.0, 2)));
        preview.setShippingCost(15.0);

        // when / then
        assertThat(InvoiceAutoMatch.isPerfect(preview)).isFalse();
    }

    @Test
    void deliveryWithoutProductsIsNotPerfect() {
        // given
        InvoiceSyncPreview preview = preview(List.of(), List.of());

        // when / then
        assertThat(InvoiceAutoMatch.isPerfect(preview)).isFalse();
    }

    @Test
    void deliveryAwaitingApprovalIsNotPerfect() {
        // given
        InvoiceSyncPreview preview = preview(List.of(mapping("pos-1", 50.0)), List.of(option("pos-1", 50.0, 2)));
        preview.setDeliveryAwaitingApproval(true);

        // when / then
        assertThat(InvoiceAutoMatch.isPerfect(preview)).isFalse();
    }

    private static InvoiceSyncPreview preview(List<InvoiceSyncPreview.Mapping> mappings, List<InvoiceSyncPreview.Option> options) {
        InvoiceSyncPreview preview = new InvoiceSyncPreview();
        preview.setMappings(new ArrayList<>(mappings));
        preview.setOptions(new ArrayList<>(options));
        return preview;
    }

    private static InvoiceSyncPreview.Mapping mapping(String positionId, double unitCost) {
        InvoiceSyncPreview.Mapping mapping = new InvoiceSyncPreview.Mapping();
        mapping.setUnitCost(unitCost);
        mapping.setSelectedPositionId(positionId);
        return mapping;
    }

    private static InvoiceSyncPreview.Option option(String id, double priceNet, int qty) {
        InvoiceSyncPreview.Option option = new InvoiceSyncPreview.Option();
        option.setId(id);
        option.setQty(qty);
        option.setPriceNet(priceNet);
        option.setTotalNet(priceNet * qty);
        return option;
    }

    private static Delivery delivery() {
        Delivery delivery = new Delivery("store-1", "ZK/1", "Acme");
        delivery.setTax(1.23);
        delivery.increaseTotalCost(100.0);
        return delivery;
    }

    private static Invoice invoice(boolean paid, boolean partiallyPaid) {
        return new Invoice("inv-1", "FV/1", null, Price.fromNet(100.0), null, "PLN", 1.0, paid, null, List.of(), null, null, partiallyPaid);
    }
}
