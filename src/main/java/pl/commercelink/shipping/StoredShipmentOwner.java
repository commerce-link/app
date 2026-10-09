package pl.commercelink.shipping;

import lombok.extern.slf4j.Slf4j;
import pl.commercelink.orders.Shipment;
import pl.commercelink.orders.ShipmentCreationState;
import pl.commercelink.orders.ShipmentLists;
import pl.commercelink.orders.ShipmentPickup;
import pl.commercelink.starter.dynamodb.OptimisticLockingExecutor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

/**
 * An owner that keeps its shipments on a stored record (an order, an RMA): every write re-reads the record and saves it
 * only when the change applied, so a message for a command the record no longer waits for changes nothing.
 */
@Slf4j
abstract class StoredShipmentOwner<T> implements ShipmentOwner {

    private final OptimisticLockingExecutor optimisticLockingExecutor;

    StoredShipmentOwner(OptimisticLockingExecutor optimisticLockingExecutor) {
        this.optimisticLockingExecutor = optimisticLockingExecutor;
    }

    protected abstract T load(String storeId, String ownerId);

    protected abstract void save(T owner);

    protected abstract List<Shipment> shipments(T owner);

    /** What creating the shipment sets off for the owner, once the created shipments are saved on it. */
    protected abstract void afterCreated(ShipmentCreationCheckRequest request);

    /**
     * Whether a shipment stays next to a new creation: one with a courier order or with data does, since dropping it
     * would lose a paid label nobody could cancel any more. A failed creation goes even with a package id: the retry
     * replaces it, and kept beside the created one it would still offer "Nadaj przesyłkę" (a second paid label).
     */
    protected static boolean keepsItsPlace(Shipment shipment) {
        return !shipment.creationFailed()
                && (shipment.getExternalId() != null || shipment.hasShippingData() || shipment.hasCollectionData());
    }

    @Override
    public void recordExternalId(ShipmentCreationCheckRequest request) {
        modify(request, owner -> ShipmentLists.creating(shipments(owner), request.getCommandId())
                .map(s -> {
                    s.setExternalId(request.getExternalId());
                    return true;
                }).orElse(false));
    }

    @Override
    public boolean awaits(ShipmentCreationCheckRequest request) {
        T owner = load(request.getStoreId(), request.getOwnerId());
        return owner != null && ShipmentLists.creating(shipments(owner), request.getCommandId()).isPresent();
    }

    @Override
    public boolean succeeded(ShipmentCreationCheckRequest request, List<Shipment> created) {
        boolean replaced = modify(request, owner -> {
            List<Shipment> list = shipments(owner);
            ShipmentLists.creating(list, request.getCommandId()).ifPresent(placeholder ->
                    created.forEach(s -> takeDeliveryChoice(s, placeholder)));
            return ShipmentLists.replaceCreating(list, request.getCommandId(), new ArrayList<>(created));
        });
        if (!replaced) {
            return false;
        }
        try {
            afterCreated(request);
        } catch (RuntimeException e) {
            // the placeholder is gone, so a redelivery would drop the message: retrying cannot bring these back
            log.error("Shipment of creation command {} was saved on {} {} in store {}, but what it sets off failed",
                    request.getCommandId(), request.getOwnerType(), request.getOwnerId(), request.getStoreId(), e);
        }
        return true;
    }

    private static void takeDeliveryChoice(Shipment created, Shipment placeholder) {
        created.setType(placeholder.getType());
        created.setCollectionPointCode(placeholder.getCollectionPointCode());
        if (created.getCarrier() == null) {
            created.setCarrier(placeholder.getCarrier());
        }
    }

    @Override
    public void refused(ShipmentCreationCheckRequest request, String error, String errorKey) {
        failed(request, error, errorKey);
    }

    @Override
    public void failed(ShipmentCreationCheckRequest request, String error, String errorKey) {
        modify(request, owner -> ShipmentLists.creating(shipments(owner), request.getCommandId())
                .map(s -> {
                    ShipmentCreationState creation = s.getCreation();
                    s.setCreation(errorKey != null ? creation.failedWithKey(errorKey) : creation.failed(error));
                    return true;
                }).orElse(false));
    }

    @Override
    public int applyPickup(String storeId, String ownerId, Collection<String> externalIds,
                           UnaryOperator<ShipmentPickup> change) {
        AtomicInteger changed = new AtomicInteger();
        modify(storeId, ownerId, owner -> {
            changed.set(ShipmentLists.applyPickup(shipments(owner), externalIds, change));
            return changed.get() > 0;
        });
        return changed.get();
    }

    /** False for a missing record or when the change did not apply; then nothing is saved. */
    protected boolean modify(ShipmentCreationCheckRequest request, Predicate<T> change) {
        return modify(request.getStoreId(), request.getOwnerId(), change);
    }

    protected boolean modify(String storeId, String ownerId, Predicate<T> change) {
        AtomicBoolean changed = new AtomicBoolean();
        optimisticLockingExecutor.modifyAndSave(
                () -> load(storeId, ownerId),
                (Consumer<T>) fresh -> changed.set(fresh != null && change.test(fresh)),
                fresh -> {
                    if (changed.get()) {
                        save(fresh);
                    }
                });
        return changed.get();
    }
}
