package pl.commercelink.receipts;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;
import pl.commercelink.notifications.StoreNotificationService;
import pl.commercelink.stores.StoreNotification;
import pl.commercelink.stores.StoreNotificationType;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ReceiptAlertsTest {

    private final StoreNotificationService notifications = mock(StoreNotificationService.class);
    private final StaticMessageSource messages = new StaticMessageSource();
    private final ReceiptAlerts alerts = new ReceiptAlerts(notifications, messages);

    private ReceiptAttempt attempt(String attention) {
        ReceiptAttempt attempt = new ReceiptAttempt();
        attempt.setStoreId("s1");
        attempt.setReceiptKey("o1:R1");
        attempt.setOrderId("o1");
        attempt.setAttention(attention);
        return attempt;
    }

    @Test
    void aNewReasonReplacesTheNotification() {
        messages.addMessage("receipts.attention.FAILED", new Locale("pl"), "Paragon {1} odrzucony: {3}");
        ReceiptAttempt attempt = attempt(ReceiptAttention.PENDING_LONG.name());
        attempt.setFailureMessage("VAT");

        assertThat(alerts.sync(attempt, ReceiptAttention.FAILED)).isTrue();

        verify(notifications).resolve("s1", StoreNotificationType.RECEIPT_ATTENTION, "o1:R1");
        verify(notifications).publish(eq("s1"), argThat((StoreNotification n) ->
                n.getType() == StoreNotificationType.RECEIPT_ATTENTION && "o1:R1".equals(n.getObject())
                        && n.getMessage().equals("Paragon o1:R1 odrzucony: VAT")));
        assertThat(attempt.getAttention()).isEqualTo("FAILED");
    }

    @Test
    void anUnchangedReasonDoesNothing() {
        assertThat(alerts.sync(attempt("FAILED"), ReceiptAttention.FAILED)).isFalse();
        verifyNoInteractions(notifications);
    }

    @Test
    void aSolvedProblemRemovesTheNotification() {
        ReceiptAttempt attempt = attempt("PENDING_LONG");

        assertThat(alerts.sync(attempt, null)).isTrue();

        verify(notifications).resolve("s1", StoreNotificationType.RECEIPT_ATTENTION, "o1:R1");
        verify(notifications, never()).publish(any(), any());
        assertThat(attempt.getAttention()).isNull();
    }
}
