package pl.commercelink.shipping;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;
import pl.commercelink.notifications.StoreNotificationService;
import pl.commercelink.orders.Shipment;
import pl.commercelink.orders.ShipmentLists;
import pl.commercelink.orders.ShipmentPickup;
import pl.commercelink.orders.event.Event;
import pl.commercelink.orders.event.EventType;
import pl.commercelink.orders.notifications.EmailNotificationType;
import pl.commercelink.orders.rma.RMA;
import pl.commercelink.orders.rma.RMACarrierConfirmationEmailNotification;
import pl.commercelink.orders.rma.RMAItemsRepository;
import pl.commercelink.orders.rma.RMALifecycle;
import pl.commercelink.orders.rma.RMARepository;
import pl.commercelink.orders.rma.RMAStatus;
import pl.commercelink.starter.dynamodb.OptimisticLockingExecutor;
import pl.commercelink.starter.email.EmailClient;
import pl.commercelink.stores.StoreNotification;
import pl.commercelink.stores.StoreNotificationSeverity;
import pl.commercelink.stores.StoreNotificationType;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Objects;

/** The shipment a customer books to send goods back: its pickup is ordered at once and the customer gets an e-mail. */
@Slf4j
@Component
public class RmaReturnShipmentOwner extends RmaShipmentOwner {

    // the bell is read by the store's staff, whose language is Polish whatever thread settles the message
    private static final Locale OPERATOR_LOCALE = Locale.forLanguageTag("pl");

    private final EmailClient emailClient;
    private final StoreNotificationService notifications;
    private final MessageSource messageSource;

    public RmaReturnShipmentOwner(RMARepository rmaRepository, OptimisticLockingExecutor optimisticLockingExecutor,
                                  RMAItemsRepository rmaItemsRepository, RMALifecycle rmaLifecycle,
                                  ShipmentTrackingSubscriber trackingSubscriber, EmailClient emailClient,
                                  StoreNotificationService notifications, MessageSource messageSource) {
        super(rmaRepository, optimisticLockingExecutor, rmaItemsRepository, rmaLifecycle, trackingSubscriber);
        this.emailClient = emailClient;
        this.notifications = notifications;
        this.messageSource = messageSource;
    }

    @Override
    public ShipmentOwnerType type() {
        return ShipmentOwnerType.RMA_RETURN;
    }

    @Override
    public void refused(ShipmentCreationCheckRequest request, String error) {
        modify(request, rma -> {
            // the customer sees the reason on the return page and submits again while the RMA is still Approved;
            // once it waits for the items only the operator books it again, from the failed row on the RMA page
            if (rma.getStatus() == RMAStatus.Approved) {
                return rma.getShipments().removeIf(s -> s.isCreationPendingFor(request.getCommandId()));
            }
            return ShipmentLists.creating(rma.getShipments(), request.getCommandId()).map(s -> {
                s.setCreation(s.getCreation().failed(error));
                return true;
            }).orElse(false);
        });
    }

    @Override
    protected void afterCreated(ShipmentCreationCheckRequest request) {
        // the customer's items change status when the goods arrive, as before; the e-mail follows the pickup
    }

    @Override
    public void failed(ShipmentCreationCheckRequest request, String error, String errorKey) {
        super.failed(request, error, errorKey);
        // keyed by the command: a return created again after a failure can fail again and must be heard of again
        notifications.publish(request.getStoreId(), new StoreNotification(StoreNotificationSeverity.WARNING,
                StoreNotificationType.RMA_RETURN_SHIPMENT_FAILED, request.getOwnerId() + ":" + request.getCommandId(),
                message("shipping.notification.return.failed", request.getOwnerId(), reason(error, errorKey))));
    }

    @Override
    public void onPickupSettled(String storeId, String provider, PickupTarget target, ShipmentPickup result) {
        if (result.isFailed()) {
            reportFailedPickup(storeId, target, result);
            return;
        }
        // recorded first and sent only by the call that recorded it: a repeated message, or a retry of the write after
        // a version conflict, never sends a second e-mail (one that fails to go out is not sent again either)
        Event sent = new Event(EventType.email, EmailNotificationType.RMA_CARRIER_CONFIRMATION.name(), LocalDateTime.now());
        boolean recorded = modify(storeId, target.ownerId(), rma -> {
            if (rma.hasEvent(sent)) {
                return false;
            }
            rma.addEvent(sent);
            return true;
        });
        if (recorded && !sendCarrierConfirmation(load(storeId, target.ownerId()))) {
            log.warn("RMA {} of store {}: the carrier confirmation e-mail was not sent", target.ownerId(), storeId);
        }
    }

    private void reportFailedPickup(String storeId, PickupTarget target, ShipmentPickup result) {
        log.error("Pickup of the return of RMA {} in store {} (package {}) was not ordered: {}", target.ownerId(),
                storeId, target.externalId(), result.getErrorKey() != null ? result.getErrorKey() : result.getError());
        // keyed by the pickup command, so ordering it again and failing again is heard of again; a pickup that failed
        // before any command was sent (no windows read) is tied to its package
        String attempt = result.getCommandId() != null ? result.getCommandId() : target.externalId();
        notifications.publish(storeId, new StoreNotification(StoreNotificationSeverity.WARNING,
                StoreNotificationType.RMA_RETURN_PICKUP_FAILED, target.ownerId() + ":" + attempt,
                message("shipping.notification.return.pickup.failed", target.ownerId(),
                        reason(result.getError(), result.getErrorKey()))));
    }

    // the e-mail the customer got when the return was booked in one step, unchanged
    private boolean sendCarrierConfirmation(RMA rma) {
        RMACarrierConfirmationEmailNotification msg = new RMACarrierConfirmationEmailNotification(
                rma.getEmail(), rma.getEmail(), rma.getRmaId(), rma.getOrderId(), rma.getShippingDetails());
        rma.getShipments().stream().map(Shipment::getTrackingUrl).filter(Objects::nonNull).distinct()
                .forEach(msg::addTrackingUrl);
        return emailClient.send(rma.getStoreId(), EmailNotificationType.RMA_CARRIER_CONFIRMATION, msg);
    }

    private String reason(String error, String errorKey) {
        if (errorKey != null) {
            return messageSource.getMessage(errorKey, null, OPERATOR_LOCALE);
        }
        return error != null ? error : "";
    }

    private String message(String key, Object... args) {
        return messageSource.getMessage(key, args, OPERATOR_LOCALE);
    }
}
