package pl.commercelink.web.settings;

import pl.commercelink.orders.notifications.EmailNotificationType;
import pl.commercelink.stores.FulfilmentConfiguration;
import pl.commercelink.stores.Store;

import java.util.List;
import java.util.Set;

import static pl.commercelink.orders.notifications.EmailNotificationType.*;

/**
 * The notifications page's summary of the customer emails: how many actually go out, how many are switched on without
 * content, and whether the customer address change lacks one of its two emails. The emails themselves are listed and
 * edited on the email templates page only, so the two pages cannot disagree about a message's state.
 */
public record NotificationOverview(int sentCount, int totalCount, int brokenCount, boolean addressChangeBlocked) {

    // ClientShippingAddressChangeService lets a customer change the delivery address only when both are enabled.
    public static final Set<EmailNotificationType> ADDRESS_CHANGE_TYPES =
            Set.of(CLIENT_VERIFICATION_CODE, ORDER_SHIPPING_ADDRESS_CHANGED);

    /** The notification types grouped the way an admin thinks of them, as the templates page lists them. */
    public static final List<GroupDefinition> GROUPS = List.of(
            new GroupDefinition("email.notification.group.orders", List.of(ORDER_CONFIRMATION, ORDER_ASSEMBLY,
                    ORDER_ASSEMBLY_DATE_CHANGED, ORDER_ASSEMBLED, ORDER_REALIZATION, ORDER_SHIPPING, ORDER_PICKUP,
                    ORDER_SHIPPING_ADDRESS_CHANGED, ORDER_REVIEW)),
            new GroupDefinition("email.notification.group.invoices", List.of(ORDER_INVOICE, ORDER_INVOICE_PROFORMA)),
            new GroupDefinition("email.notification.group.returns", List.of(RMA_CARRIER_ARRANGEMENT,
                    RMA_CARRIER_CONFIRMATION, RMA_ITEMS_RECEIVED, RMA_PROCESSING_STARTED, RMA_ITEMS_ACCEPTED,
                    RMA_REJECTED, RMA_ITEMS_SEND_TO_CLIENT)),
            new GroupDefinition("email.notification.group.clientVerification", List.of(CLIENT_VERIFICATION_CODE)));

    public record GroupDefinition(String labelKey, List<EmailNotificationType> types) {
    }

    public static NotificationOverview of(Store store, List<EmailTemplateView> emails) {
        int sent = (int) emails.stream().filter(EmailTemplateView::sent).count();
        int broken = (int) emails.stream().filter(EmailTemplateView::broken).count();
        return new NotificationOverview(sent, emails.size(), broken, addressChangeBlocked(store, emails));
    }

    /** The customer address change is on, but one of its two emails would not go out. */
    public static boolean addressChangeBlocked(Store store, List<EmailTemplateView> emails) {
        return addressChangeOn(store) && !emails.stream()
                .filter(email -> ADDRESS_CHANGE_TYPES.contains(email.type()))
                .allMatch(EmailTemplateView::sent);
    }

    public static boolean addressChangeOn(Store store) {
        FulfilmentConfiguration fulfilment = store.getFulfilmentConfiguration();
        return fulfilment != null && fulfilment.isClientShippingAddressChangeEnabled();
    }
}
