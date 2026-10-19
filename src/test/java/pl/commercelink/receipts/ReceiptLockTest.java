package pl.commercelink.receipts;

import org.junit.jupiter.api.Test;
import pl.commercelink.orders.Order;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReceiptLockTest {

    @Test
    void onlyTheLastReceiptSegmentOfAKeyIsReworded() {
        // when / then
        assertThat(ReceiptLock.ATTACHING.key("order.items.add.locked.receipt"))
                .isEqualTo("order.items.add.locked.receiptAttaching");
        assertThat(ReceiptLock.ATTACH_FAILED.key("order.bulk.unavailable.receipt.short"))
                .isEqualTo("order.bulk.unavailable.receiptAttachFailed.short");
        // a ".receipt" earlier in the key is left alone
        assertThat(ReceiptLock.ATTACHING.key("order.receipt.form.locked.receipt"))
                .isEqualTo("order.receipt.form.locked.receiptAttaching");
        assertThat(ReceiptLock.ISSUING.key("order.page.cancel.locked.receipt")).isEqualTo("order.page.cancel.locked.receipt");
    }

    @Test
    void aKeyThatIsNotAReceiptLockKeyIsRefused() {
        // when / then
        assertThatThrownBy(() -> ReceiptLock.ATTACHING.key("receipts.action.reissue"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ReceiptLock.ISSUING.key("order.items.add.locked.receipts"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aFiscalisedAttemptWhoseEffectsKeepFailingLocksAsAttachFailed() {
        // given
        ReceiptAttempt stuck = new ReceiptAttempt();
        stuck.setState(ReceiptAttemptState.FISCALISED);
        stuck.setEffectsFailures(ReceiptAttentionEvaluator.EFFECTS_FAILURES_BEFORE_ALERT);
        ReceiptAttempt retrying = new ReceiptAttempt();
        retrying.setState(ReceiptAttemptState.FISCALISED);
        retrying.setEffectsFailures(ReceiptAttentionEvaluator.EFFECTS_FAILURES_BEFORE_ALERT - 1);
        Order order = new Order("s1");

        // when / then
        assertThat(ReceiptOrderState.receiptLock(List.of(stuck), order)).isEqualTo(ReceiptLock.ATTACH_FAILED);
        assertThat(ReceiptOrderState.receiptLock(List.of(retrying), order)).isEqualTo(ReceiptLock.ATTACHING);
    }
}
