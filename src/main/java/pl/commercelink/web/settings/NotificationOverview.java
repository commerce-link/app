package pl.commercelink.web.settings;

import pl.commercelink.orders.notifications.EmailNotificationType;
import pl.commercelink.stores.FulfilmentConfiguration;
import pl.commercelink.stores.Store;

import java.util.List;
import java.util.Set;

import static pl.commercelink.orders.notifications.EmailNotificationType.*;

/** Which emails the store sends to customers, grouped the way an admin thinks of them, each linked to its template. */
public record NotificationOverview(List<Group> groups, int enabledCount, int totalCount, boolean addressChangeBlocked) {

    public record Group(String labelKey, List<Item> items) {
    }

    public record Item(EmailNotificationType type, String labelKey, boolean enabled, boolean requiredForAddressChange,
                       String templateHref) {
    }

    // ClientShippingAddressChangeService lets a customer change the delivery address only when both are enabled.
    private static final Set<EmailNotificationType> ADDRESS_CHANGE_TYPES =
            Set.of(CLIENT_VERIFICATION_CODE, ORDER_SHIPPING_ADDRESS_CHANGED);

    private static final List<GroupDefinition> GROUPS = List.of(
            new GroupDefinition("email.notification.group.orders", List.of(ORDER_CONFIRMATION, ORDER_ASSEMBLY,
                    ORDER_ASSEMBLY_DATE_CHANGED, ORDER_ASSEMBLED, ORDER_REALIZATION, ORDER_SHIPPING, ORDER_PICKUP,
                    ORDER_SHIPPING_ADDRESS_CHANGED, ORDER_REVIEW)),
            new GroupDefinition("email.notification.group.invoices", List.of(ORDER_INVOICE, ORDER_INVOICE_PROFORMA)),
            new GroupDefinition("email.notification.group.returns", List.of(RMA_CARRIER_ARRANGEMENT,
                    RMA_CARRIER_CONFIRMATION, RMA_ITEMS_RECEIVED, RMA_PROCESSING_STARTED, RMA_ITEMS_ACCEPTED,
                    RMA_REJECTED, RMA_ITEMS_SEND_TO_CLIENT)),
            new GroupDefinition("email.notification.group.clientVerification", List.of(CLIENT_VERIFICATION_CODE)));

    private record GroupDefinition(String labelKey, List<EmailNotificationType> types) {
    }

    public static NotificationOverview of(Store store, String emailTemplatesPath) {
        List<Group> groups = GROUPS.stream()
                .map(definition -> new Group(definition.labelKey(), definition.types().stream()
                        .map(type -> new Item(type, "email.notification.type." + type.name(),
                                store.supportsNotification(type), ADDRESS_CHANGE_TYPES.contains(type),
                                emailTemplatesPath + "?selectedType=" + type.name()))
                        .toList()))
                .toList();
        int enabled = (int) groups.stream().flatMap(group -> group.items().stream()).filter(Item::enabled).count();
        int total = (int) groups.stream().mapToLong(group -> group.items().size()).sum();
        FulfilmentConfiguration fulfilment = store.getFulfilmentConfiguration();
        boolean addressChangeOn = fulfilment != null && fulfilment.isClientShippingAddressChangeEnabled();
        boolean blocked = addressChangeOn && !ADDRESS_CHANGE_TYPES.stream().allMatch(store::supportsNotification);
        return new NotificationOverview(groups, enabled, total, blocked);
    }
}
