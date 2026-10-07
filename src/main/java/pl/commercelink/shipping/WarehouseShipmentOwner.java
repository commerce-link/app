package pl.commercelink.shipping;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;
import pl.commercelink.documents.Document;
import pl.commercelink.notifications.StoreNotificationService;
import pl.commercelink.orders.Shipment;
import pl.commercelink.orders.ShipmentPickup;
import pl.commercelink.starter.util.OperationResult;
import pl.commercelink.stores.StoreNotification;
import pl.commercelink.stores.StoreNotificationSeverity;
import pl.commercelink.stores.StoreNotificationType;
import pl.commercelink.warehouse.builtin.WarehouseGoodsOutService;
import pl.commercelink.warehouse.builtin.WarehouseShippingReservations;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.function.UnaryOperator;

/**
 * A shipment sent from the warehouse. No shipment is stored for it: the check message carries what settling needs, the
 * items are held for the command until their goods-out, and the store learns the outcome from its notifications.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WarehouseShipmentOwner implements ShipmentOwner {

    // the bell is read by the store's staff, whose language is Polish whatever thread settles the message
    private static final Locale OPERATOR_LOCALE = Locale.forLanguageTag("pl");

    private final StoreNotificationService notifications;
    private final WarehouseGoodsOutService goodsOutService;
    private final WarehouseShippingReservations reservations;
    private final MessageSource messageSource;

    @Override
    public ShipmentOwnerType type() {
        return ShipmentOwnerType.WAREHOUSE;
    }

    @Override
    public boolean markCreating(ShipmentCreationCheckRequest request, Shipment placeholder) {
        // the shipment itself is not stored, but its items are held: a second shipment of them would pay a second label
        return reservations.hold(request.getStoreId(), request.getItemIds(), request.getCommandId());
    }

    @Override
    public void recordExternalId(ShipmentCreationCheckRequest request) {
        // nothing stored
    }

    @Override
    public void refused(ShipmentCreationCheckRequest request, String error) {
        // the operator sees the reason on the shipping page and may ship the items again
        reservations.release(request.getStoreId(), request.getItemIds(), request.getCommandId());
    }

    @Override
    public boolean awaits(ShipmentCreationCheckRequest request) {
        // nothing stored to wait on: the message itself is the only record of the command
        return true;
    }

    @Override
    public boolean succeeded(ShipmentCreationCheckRequest request, List<Shipment> created) {
        Shipment first = created.get(0);
        String message = message("shipping.notification.warehouse.created", first.getTrackingNo(), first.getCarrier());
        // nothing else records the command, so the notification is the guard: a repeated message finds it and
        // issues no second goods-out (nor a second pickup, which follows only a true here)
        boolean isNew = notifications.publish(request.getStoreId(), new StoreNotification(StoreNotificationSeverity.INFO,
                StoreNotificationType.WAREHOUSE_SHIPMENT_CREATED, createdObject(request), message));
        if (!isNew) {
            return false;
        }
        issueGoodsOut(request);
        // out of stock now (or to be issued by hand, see the error): the hold has done its job
        reservations.release(request.getStoreId(), request.getItemIds(), request.getCommandId());
        return true;
    }

    // provider:externalId names the label to download; without a package id the command still tells creations apart
    private static String createdObject(ShipmentCreationCheckRequest request) {
        return StringUtils.isBlank(request.getExternalId()) ? request.getCommandId()
                : packageObject(request.getProvider(), request.getExternalId());
    }

    private static String packageObject(String provider, String externalId) {
        return provider + ":" + externalId;
    }

    private void issueGoodsOut(ShipmentCreationCheckRequest request) {
        try {
            OperationResult<Document> result = goodsOutService.issueGoodsOutForExternalService(request.getStoreId(),
                    request.getItemIds(), request.getReceiver(), request.getIssuedBy());
            if (!result.isSuccess()) {
                log.error("Warehouse shipment {} (command {}) was created but its goods-out failed in store {}: {}; "
                                + "issue it by hand", request.getExternalId(), request.getCommandId(),
                        request.getStoreId(), result.getMessage());
            }
        } catch (RuntimeException e) {
            log.error("Warehouse shipment {} (command {}) was created but its goods-out failed in store {}: {}; "
                    + "issue it by hand", request.getExternalId(), request.getCommandId(), request.getStoreId(),
                    e.getMessage(), e);
        }
    }

    @Override
    public void failed(ShipmentCreationCheckRequest request, String error, String errorKey) {
        reservations.release(request.getStoreId(), request.getItemIds(), request.getCommandId());
        notifications.publish(request.getStoreId(), new StoreNotification(StoreNotificationSeverity.WARNING,
                StoreNotificationType.WAREHOUSE_SHIPMENT_FAILED, request.getCommandId(),
                message("shipping.notification.warehouse.failed", reason(error, errorKey))));
    }

    @Override
    public int applyPickup(String storeId, String ownerId, Collection<String> externalIds,
                           UnaryOperator<ShipmentPickup> change) {
        // nothing stored: counted as applied, so ordering and settling the pickup go on
        return externalIds.size();
    }

    @Override
    public void onPickupSettled(String storeId, String provider, PickupTarget target, ShipmentPickup result) {
        String key = result.isBookedByCarrier() ? "shipping.notification.warehouse.pickup.carrier"
                : result.isOrdered() ? "shipping.notification.warehouse.pickup.ordered"
                : result.isFailed() ? "shipping.notification.warehouse.pickup.failed"
                : "shipping.notification.warehouse.pickup.point";
        String message = message(key, target.trackingNo(), result.getDate(), result.getFrom(), result.getTo(),
                reason(result.getError(), result.getErrorKey()), result.getPickupId());
        notifications.publish(storeId, new StoreNotification(
                result.isFailed() ? StoreNotificationSeverity.WARNING : StoreNotificationSeverity.INFO,
                StoreNotificationType.WAREHOUSE_SHIPMENT_PICKUP, packageObject(provider, target.externalId()), message));
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
