package pl.commercelink.web.dtos;

import lombok.Getter;
import lombok.Setter;
import pl.commercelink.orders.OrderSourceType;
import pl.commercelink.orders.notifications.EmailNotificationType;
import pl.commercelink.stores.ClientNotificationsConfiguration;
import pl.commercelink.stores.ReceiptConfiguration;
import pl.commercelink.stores.Store;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Settings › E-receipts: whether delivered consumer orders get automatic e-receipts, and from which sources. */
@Getter
@Setter
public class ReceiptSettingsForm {

    private boolean enabled;
    private List<String> sources = new ArrayList<>();

    public static ReceiptSettingsForm from(ReceiptConfiguration configuration) {
        ReceiptSettingsForm form = new ReceiptSettingsForm();
        form.enabled = configuration.isEnabled();
        form.sources = configuration.sourceTypes().stream().map(Enum::name).sorted().toList();
        return form;
    }

    public Map<String, String> validate() {
        Map<String, String> errors = new LinkedHashMap<>();
        if (enabled && (sources == null || sources.isEmpty())) {
            errors.put("sources", "store.receipts.sources.required");
        }
        if (sources != null && sources.stream().anyMatch(s -> !isSource(s))) {
            errors.put("sources", "store.receipts.sources.unknown");
        }
        return errors;
    }

    public void applyTo(Store store, LocalDateTime now) {
        ReceiptConfiguration configuration = store.getReceiptConfiguration();
        boolean firstTime = configuration.getEnabledAt() == null;
        Set<OrderSourceType> types = EnumSet.noneOf(OrderSourceType.class);
        (sources == null ? List.<String>of() : sources).forEach(s -> types.add(OrderSourceType.valueOf(s)));
        configuration.setSourceTypes(types);
        if (enabled) {
            configuration.enable(now);
            if (firstTime) {
                ClientNotificationsConfiguration notifications = store.getClientNotificationsConfiguration();
                if (notifications == null) {
                    notifications = new ClientNotificationsConfiguration();
                    store.setClientNotificationsConfiguration(notifications);
                }
                if (!notifications.supports(EmailNotificationType.ORDER_RECEIPT)) {
                    notifications.enableNotification(EmailNotificationType.ORDER_RECEIPT,
                            EmailNotificationType.ORDER_RECEIPT.getTemplateName());
                }
            }
        } else {
            configuration.disable();
        }
    }

    private static boolean isSource(String value) {
        try {
            OrderSourceType.valueOf(value);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
