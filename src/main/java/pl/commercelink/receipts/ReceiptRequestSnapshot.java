package pl.commercelink.receipts;

import pl.commercelink.receipts.api.LineKind;
import pl.commercelink.receipts.api.Money;
import pl.commercelink.receipts.api.PaymentForm;
import pl.commercelink.receipts.api.ReceiptBuyer;
import pl.commercelink.receipts.api.ReceiptLine;
import pl.commercelink.receipts.api.ReceiptMedium;
import pl.commercelink.receipts.api.ReceiptPayment;
import pl.commercelink.receipts.api.ReceiptRequest;
import pl.commercelink.receipts.api.VatRate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * What an attempt fiscalises, frozen when the attempt is created: every retry of the key sends exactly this, whatever
 * happened to the order since.
 */
public record ReceiptRequestSnapshot(String orderId, LocalDateTime saleDate, String buyerEmail,
                                     List<Line> lines, List<Pay> payments) {

    public record Line(LineKind kind, String name, BigDecimal quantity, long unitGrosze, VatRate vatRate,
                       String sku, String ean) {
    }

    public record Pay(PaymentForm form, long grosze, String label) {
    }

    /** Builds (and so validates) the contract request under the attempt's key. */
    public ReceiptRequest toRequest(String receiptKey) {
        ReceiptRequest.Builder builder = ReceiptRequest.builder()
                .receiptKey(receiptKey)
                .orderId(orderId)
                .saleDate(saleDate)
                .medium(ReceiptMedium.ELECTRONIC)
                .buyer(ReceiptBuyer.builder().email(buyerEmail).build());
        for (Line line : lines) {
            Money unit = Money.ofGrosze(line.unitGrosze());
            ReceiptLine.Builder lineBuilder = switch (line.kind()) {
                case SHIPPING -> ReceiptLine.shipping(line.name(), unit, line.vatRate());
                case SERVICE -> ReceiptLine.service(line.name(), line.quantity(), unit, line.vatRate());
                default -> ReceiptLine.goods(line.name(), line.quantity(), unit, line.vatRate());
            };
            builder.line(lineBuilder.sku(line.sku()).ean(line.ean()));
        }
        for (Pay pay : payments) {
            builder.payment(ReceiptPayment.of(pay.form(), Money.ofGrosze(pay.grosze())).label(pay.label()));
        }
        return builder.build();
    }
}
