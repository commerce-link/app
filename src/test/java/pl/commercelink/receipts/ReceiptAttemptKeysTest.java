package pl.commercelink.receipts;

import org.junit.jupiter.api.Test;
import pl.commercelink.receipts.api.ReceiptKeys;

import static org.assertj.core.api.Assertions.assertThat;

class ReceiptAttemptKeysTest {

    @Test
    void uuidOrderIdsKeepTheirId() {
        String key = ReceiptAttemptKeys.of("0f3c2a8e-1b2c-4d5e-8f90-123456789abc", 1);

        assertThat(key).isEqualTo("0f3c2a8e-1b2c-4d5e-8f90-123456789abc:R1");
        assertThat(ReceiptKeys.requireValid(key)).isEqualTo(key);
        assertThat(ReceiptAttemptKeys.orderPartOf(key)).isEqualTo("0f3c2a8e-1b2c-4d5e-8f90-123456789abc");
    }

    @Test
    void idsOutsideTheKeyAlphabetAreHashed() {
        String key = ReceiptAttemptKeys.of("zamówienie/12", 3);

        assertThat(key).matches("o[0-9a-f]{32}:R3");
        assertThat(ReceiptKeys.requireValid(key)).isEqualTo(key);
        assertThat(ReceiptAttemptKeys.of("zamówienie/12", 3)).isEqualTo(key);
    }

    @Test
    void prefixSeparatesAttemptsOfOrdersSharingABeginning() {
        assertThat(ReceiptAttemptKeys.orderPrefix("abc")).isEqualTo("abc:");
        assertThat(ReceiptAttemptKeys.of("abc", 12)).startsWith(ReceiptAttemptKeys.orderPrefix("abc"));
        assertThat(ReceiptAttemptKeys.of("abcd", 1)).doesNotStartWith(ReceiptAttemptKeys.orderPrefix("abc"));
    }
}
