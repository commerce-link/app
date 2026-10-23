package pl.commercelink.inventory.deliveries;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;
import pl.commercelink.inventory.supplier.SupplierLabels;
import pl.commercelink.notifications.StoreNotificationService;
import pl.commercelink.orders.event.EventType;
import pl.commercelink.orders.event.OrderEvent;
import pl.commercelink.orders.event.OrderEventsRepository;
import pl.commercelink.stores.StoreNotification;
import pl.commercelink.stores.StoreNotificationSeverity;
import pl.commercelink.stores.StoreNotificationType;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Objects;

/**
 * Tells the store why the platform administrator rejected its delivery request: a bell notification for the store and
 * an event in the history of every customer order the request carried items for. The delivery itself is deleted by the
 * rejection, so these are the only traces of the reason. Runs after the rejection; a failure is logged and never undoes it.
 */
@Component
@Slf4j
public class DeliveryRequestRejectionRecorder {

    static final int MAX_REASON_LENGTH = 500;
    private static final Locale OPERATOR_LOCALE = Locale.forLanguageTag("pl");

    private final StoreNotificationService notifications;
    private final OrderEventsRepository orderEvents;
    private final MessageSource messageSource;
    private final SupplierLabels supplierLabels;

    public DeliveryRequestRejectionRecorder(StoreNotificationService notifications, OrderEventsRepository orderEvents,
                                            MessageSource messageSource, SupplierLabels supplierLabels) {
        this.notifications = notifications;
        this.orderEvents = orderEvents;
        this.messageSource = messageSource;
        this.supplierLabels = supplierLabels;
    }

    public void record(String storeId, Delivery delivery, String reason) {
        try {
            String cleanReason = normalizedReason(reason);
            String supplier = supplierLabels.forStoreId(storeId).of(delivery.getProvider());
            notify(storeId, delivery, supplier, cleanReason);
            String details = cleanReason == null ? supplier : supplier + " · " + cleanReason;
            delivery.getAllocations().stream()
                    .map(Allocation::getKey)
                    .filter(Objects::nonNull)
                    .map(AllocationKey::getOrderId)
                    .filter(StringUtils::isNotBlank)
                    .distinct()
                    .forEach(orderId -> saveEvent(orderId, details));
        } catch (RuntimeException e) {
            log.error("Could not record the rejected delivery request {} of store {}", delivery.getDeliveryId(), storeId, e);
        }
    }

    private void notify(String storeId, Delivery delivery, String supplier, String reason) {
        try {
            notifications.publish(storeId, new StoreNotification(StoreNotificationSeverity.WARNING,
                    StoreNotificationType.DELIVERY_REQUEST_REJECTED, delivery.getDeliveryId(),
                    message(delivery, supplier, reason)));
        } catch (RuntimeException e) {
            log.error("Could not notify store {} about the rejected delivery request {}", storeId, delivery.getDeliveryId(), e);
        }
    }

    private void saveEvent(String orderId, String details) {
        try {
            OrderEvent event = new OrderEvent(orderId, EventType.action, OrderEvent.DELIVERY_REQUEST_REJECTED, LocalDateTime.now());
            event.setDetails(details);
            orderEvents.save(event);
        } catch (RuntimeException e) {
            log.error("Could not record the rejected delivery request in the history of order {}", orderId, e);
        }
    }

    private String message(Delivery delivery, String supplier, String reason) {
        int pieces = delivery.getAllocations().stream().mapToInt(Allocation::getQty).sum();
        double net = delivery.getAllocations().stream().mapToDouble(Allocation::getTotalCost).sum();
        String reasonPart = reason == null ? ""
                : messageSource.getMessage("deliveries.approval.rejected.notification.reason", new Object[]{reason}, OPERATOR_LOCALE);
        return messageSource.getMessage("deliveries.approval.rejected.notification",
                new Object[]{supplier, shortId(delivery.getDeliveryId()), String.valueOf(pieces), amount(net), reasonPart},
                OPERATOR_LOCALE);
    }

    static String normalizedReason(String reason) {
        String trimmed = StringUtils.trimToNull(reason);
        return trimmed == null ? null : StringUtils.left(trimmed, MAX_REASON_LENGTH);
    }

    private static String shortId(String deliveryId) {
        return deliveryId == null ? "" : StringUtils.left(deliveryId, 8);
    }

    // the Polish locale groups thousands with a no-break space (U+00A0) or a narrow one (U+202F, newer JDKs)
    private static String amount(double net) {
        return String.format(OPERATOR_LOCALE, "%,.2f", net).replace(' ', ' ').replace(' ', ' ');
    }
}
