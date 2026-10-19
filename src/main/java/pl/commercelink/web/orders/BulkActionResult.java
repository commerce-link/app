package pl.commercelink.web.orders;

import org.springframework.context.MessageSource;
import pl.commercelink.orders.OrdersManager;

import java.util.Locale;

/** "Changed: N of M" and, when something was left, why — never skipped silently. */
public record BulkActionResult(BulkAction action, int changed, int requested, int skippedDropship) {

    public static BulkActionResult of(BulkAction action, OrdersManager.Result result) {
        return new BulkActionResult(action, result.getChanged(), result.getRequested(), result.getSkippedDropshipItems());
    }

    public boolean complete() {
        return changed == requested;
    }

    public String message(MessageSource messages, Locale locale) {
        String outcome = messages.getMessage("order.bulk.result", new Object[]{changed, requested}, locale);
        if (complete()) {
            return outcome;
        }
        String reason = skippedDropship > 0
                ? messages.getMessage("order.bulk.skipped.dropship", null, locale)
                : messages.getMessage(action.skippedKey(), null, locale);
        return outcome + " " + reason;
    }
}
