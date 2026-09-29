package pl.commercelink.receipts;

import org.junit.jupiter.api.Test;
import pl.commercelink.receipts.api.LineKind;
import pl.commercelink.receipts.api.PaymentForm;
import pl.commercelink.receipts.api.ReceiptRequest;
import pl.commercelink.receipts.api.VatRate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReceiptRequestSnapshotTest {

    @Test
    void survivesJsonAndBuildsTheSameRequestForEveryRetry() {
        ReceiptRequestSnapshot snapshot = new ReceiptRequestSnapshot("o1", LocalDateTime.of(2026, 9, 23, 12, 0),
                "jan@example.com",
                List.of(new ReceiptRequestSnapshot.Line(LineKind.GOODS, "Mysz", new BigDecimal("2"), 5000, VatRate.VAT_23, "S", null)),
                List.of(new ReceiptRequestSnapshot.Pay(PaymentForm.CARD, 10000, null)));

        ReceiptRequestSnapshot read = ReceiptSnapshotJson.read(ReceiptSnapshotJson.write(snapshot));

        assertThat(read).isEqualTo(snapshot);

        // ReceiptRequest has no equals(); compare the fields it exposes instead (ReceiptLine, ReceiptPayment and
        // ReceiptBuyer do have equals()).
        ReceiptRequest expected = snapshot.toRequest("o1:R1");
        ReceiptRequest actual = read.toRequest("o1:R1");
        assertThat(actual.receiptKey()).isEqualTo(expected.receiptKey());
        assertThat(actual.orderId()).isEqualTo(expected.orderId());
        assertThat(actual.saleDate()).isEqualTo(expected.saleDate());
        assertThat(actual.medium()).isEqualTo(expected.medium());
        assertThat(actual.lines()).isEqualTo(expected.lines());
        assertThat(actual.payments()).isEqualTo(expected.payments());
        assertThat(actual.buyer()).isEqualTo(expected.buyer());
    }
}
