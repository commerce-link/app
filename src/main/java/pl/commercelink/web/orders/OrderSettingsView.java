package pl.commercelink.web.orders;

import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.orders.fulfilment.FulfilmentType;

import java.util.List;

/** The read-only "Terminy i ustawienia" card and the form of its dialog and no-JS page. */
public record OrderSettingsView(String estimatedAssemblyAt, String estimatedShippingAt, String preferredShippingAt,
                                String estimatedAssemblyText, String estimatedShippingText, String preferredShippingText,
                                FulfilmentType fulfilmentType, String fulfilmentTypeShortKey, String fulfilmentTypeIcon,
                                boolean fulfilmentTypeLocked, boolean emailNotificationsEnabled, String comment,
                                String affiliateId, String gclid, boolean editable,
                                List<OrderLabels.Option<FulfilmentType>> fulfilmentTypes) {

    public static OrderSettingsView of(Order order, List<OrderItem> items, boolean readOnly) {
        return new OrderSettingsView(
                OrderFormats.isoDate(order.getEstimatedAssemblyAt()), OrderFormats.isoDate(order.getEstimatedShippingAt()),
                OrderFormats.isoDate(order.getPreferredShippingAt()),
                OrderFormats.date(order.getEstimatedAssemblyAt()), OrderFormats.date(order.getEstimatedShippingAt()),
                OrderFormats.date(order.getPreferredShippingAt()),
                order.getFulfilmentType(), OrderLabels.fulfilmentTypeShort(order.getFulfilmentType()),
                OrderLabels.fulfilmentTypeIcon(order.getFulfilmentType()),
                !order.canChangeFulfilmentType(items), order.isEmailNotificationsEnabled(), order.getComment(),
                order.getAffiliateId(), order.getGclid(), !readOnly,
                OrderLabels.Option.of(FulfilmentType.values(), OrderLabels::fulfilmentType));
    }
}
