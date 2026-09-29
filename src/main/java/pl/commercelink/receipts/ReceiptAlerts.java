package pl.commercelink.receipts;

import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;
import pl.commercelink.notifications.StoreNotificationService;
import pl.commercelink.receipts.api.ReceiptProviderDescriptor;
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
    private final ReceiptProviderFactory providerFactory;

    public ReceiptAlerts(StoreNotificationService notifications, MessageSource messageSource,
                         ReceiptProviderFactory providerFactory) {
        this.notifications = notifications;
        this.messageSource = messageSource;
        this.providerFactory = providerFactory;
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

    /**
     * Why a dead attempt fiscalised nothing, in a few words and without advice (the advice of {@link #message} is
     * about the newest attempt only): the blocked reason with its detail, or the provider's refusal. Null when the
     * attempt carries neither.
     */
    public String outcome(ReceiptAttempt attempt, Locale locale) {
        if (attempt.getState() == ReceiptAttemptState.BLOCKED && attempt.getBlockedReason() != null) {
            String blocked = messageSource.getMessage("receipts.blocked." + attempt.getBlockedReason(), null,
                    attempt.getBlockedReason(), locale);
            return blocked + (attempt.getBlockedDetail() == null ? "" : " (" + attempt.getBlockedDetail() + ")");
        }
        if (attempt.getState() == ReceiptAttemptState.FAILED) {
            return attempt.getFailureMessage();
        }
        return null;
    }

    /**
     * The problem as the order page shows it in the e-receipt row: unlike the bell {@link #message}, it names no
     * receipt key or order id (the row is the receipt), points at a button of the same row and keeps the provider's
     * technical hints apart, under a disclosure. Each text may be overridden per provider with a key suffixed by the
     * provider id (e.g. {@code .fakturownia}), because those hints (fiscal_status, the print marker) are specific to
     * one provider and would mislead the operator of another.
     */
    public ReceiptPageProblem pageProblem(ReceiptAttempt attempt, ReceiptAttention attention, Locale locale) {
        String providerName = providerName(attempt.getProvider(), locale);
        Object[] args = {
                providerName,
                clause(attempt.getLastError(), locale),
                clause(attempt.getFailureMessage(), locale),
                blockedText(attempt, locale),
                attempt.getIssueCalls(),
                attempt.getReceiptKey()
        };
        String base = "receipts.page." + attention.name() + ".";
        String cause = pageText(base + "cause", attempt.getProvider(), args, locale);
        String action = pageText(base + "action", attempt.getProvider(), args, locale);
        String details = pageText(base + "details", attempt.getProvider(), args, locale);
        String summary = details == null ? null : messageSource.getMessage("receipts.page.details.summary",
                new Object[]{providerName}, locale);
        return new ReceiptPageProblem(cause == null ? attention.name() : cause, action, summary, details);
    }

    /** The provider-specific text when the provider has one, the generic one otherwise; null when neither exists. */
    private String pageText(String key, String providerId, Object[] args, Locale locale) {
        String specific = providerId == null ? null : messageSource.getMessage(key + "." + providerId, args, null, locale);
        return specific != null ? specific : messageSource.getMessage(key, args, null, locale);
    }

    /**
     * The attempt's provider (stored when it was created, so a later switch of the store's system does not rename
     * old attempts) by its display name; the stored id when its adapter is no longer installed.
     */
    private String providerName(String providerId, Locale locale) {
        if (providerId == null || providerId.isBlank()) {
            return messageSource.getMessage("receipts.page.provider.unknown", null, locale);
        }
        ReceiptProviderDescriptor descriptor = providerFactory.getDescriptor(providerId);
        return descriptor == null || descriptor.displayName() == null ? providerId : descriptor.displayName();
    }

    /** Free provider text dropped into a sentence that ends with its own full stop. */
    private String clause(String text, Locale locale) {
        if (text == null || text.isBlank()) {
            return messageSource.getMessage("receipts.page.noDetail", null, locale);
        }
        String trimmed = text.strip();
        return trimmed.endsWith(".") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }

    private String blockedText(ReceiptAttempt attempt, Locale locale) {
        if (attempt.getBlockedReason() == null) {
            return messageSource.getMessage("receipts.page.noDetail", null, locale);
        }
        String blocked = messageSource.getMessage("receipts.blocked." + attempt.getBlockedReason(), null,
                attempt.getBlockedReason(), locale);
        return blocked + (attempt.getBlockedDetail() == null ? "" : " (" + attempt.getBlockedDetail() + ")");
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
