package pl.commercelink.receipts;

import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;
import pl.commercelink.notifications.StoreNotificationService;
import pl.commercelink.stores.StoreNotification;
import pl.commercelink.stores.StoreNotificationSeverity;
import pl.commercelink.stores.StoreNotificationType;

import java.util.Locale;
import java.util.Objects;

/**
 * Keeps one bell notification per attempt in line with its current problem. The notification store keeps the first
 * record of an id, so a changed reason is resolved first and published again (and so shows as unread).
 */
@Component
public class ReceiptAlerts {

    private static final Locale OPERATOR_LOCALE = Locale.forLanguageTag("pl");

    private final StoreNotificationService notifications;
    private final MessageSource messageSource;

    public ReceiptAlerts(StoreNotificationService notifications, MessageSource messageSource) {
        this.notifications = notifications;
        this.messageSource = messageSource;
    }

    /** Returns whether the attempt's stored reason changed (the caller saves the attempt). */
    public boolean sync(ReceiptAttempt attempt, ReceiptAttention attention) {
        String name = attention == null ? null : attention.name();
        if (Objects.equals(name, attempt.getAttention())) {
            return false;
        }
        notifications.resolve(attempt.getStoreId(), StoreNotificationType.RECEIPT_ATTENTION, attempt.getReceiptKey());
        if (attention != null) {
            notifications.publish(attempt.getStoreId(), new StoreNotification(StoreNotificationSeverity.WARNING,
                    StoreNotificationType.RECEIPT_ATTENTION, attempt.getReceiptKey(), message(attempt, attention)));
        }
        attempt.setAttention(name);
        return true;
    }

    /**
     * Resolves this attempt's bell notification, e.g. because a later attempt of the same order fiscalised and the
     * old FAILED/BLOCKED alert no longer needs the operator's attention. The attempt's stored {@code attention} is
     * left as-is: the order page still shows why that dead attempt needed correcting.
     */
    public void resolve(ReceiptAttempt attempt) {
        notifications.resolve(attempt.getStoreId(), StoreNotificationType.RECEIPT_ATTENTION, attempt.getReceiptKey());
    }

    /** Bell notifications always read in the fixed operator locale, whatever the caller's own request locale is. */
    public String message(ReceiptAttempt attempt, ReceiptAttention attention) {
        return message(attempt, attention, OPERATOR_LOCALE);
    }

    /** Same message, in the given locale: the order page shows it to the operator viewing it in their own language. */
    public String message(ReceiptAttempt attempt, ReceiptAttention attention, Locale locale) {
        String blocked = attempt.getBlockedReason() == null ? "" : messageSource.getMessage(
                "receipts.blocked." + attempt.getBlockedReason(), null, attempt.getBlockedReason(), locale);
        Object[] args = {
                attempt.getOrderId(),
                attempt.getReceiptKey(),
                attempt.getLastError() == null ? "" : attempt.getLastError(),
                attempt.getFailureMessage() == null ? "" : attempt.getFailureMessage(),
                blocked + (attempt.getBlockedDetail() == null ? "" : " (" + attempt.getBlockedDetail() + ")"),
                attempt.getIssueCalls()
        };
        return messageSource.getMessage(attention.messageKey(), args, attention.name(), locale);
    }
}
