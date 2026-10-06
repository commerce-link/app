package pl.commercelink.shipping;

import pl.commercelink.orders.Shipment;
import pl.commercelink.orders.ShipmentLists;
import pl.commercelink.starter.dynamodb.OptimisticLockingExecutor;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * An owner that keeps its shipments on a stored record (an order, an RMA): every write re-reads the record and saves it
 * only when the change applied, so a message for a command the record no longer waits for changes nothing.
 */
abstract class StoredShipmentOwner<T> implements ShipmentOwner {

    private final OptimisticLockingExecutor optimisticLockingExecutor;

    StoredShipmentOwner(OptimisticLockingExecutor optimisticLockingExecutor) {
        this.optimisticLockingExecutor = optimisticLockingExecutor;
    }

    protected abstract T load(String storeId, String ownerId);

    protected abstract void save(T owner);

    protected abstract List<Shipment> shipments(T owner);

    @Override
    public void recordExternalId(ShipmentCreationCheckRequest request) {
        modify(request, owner -> ShipmentLists.creating(shipments(owner), request.getCommandId())
                .map(s -> {
                    s.setExternalId(request.getExternalId());
                    return true;
                }).orElse(false));
    }

    @Override
    public void refused(ShipmentCreationCheckRequest request, String error) {
        modify(request, owner -> ShipmentLists.creating(shipments(owner), request.getCommandId())
                .map(s -> {
                    s.setCreation(s.getCreation().failed(error));
                    return true;
                }).orElse(false));
    }

    /** False for a missing record or when the change did not apply; then nothing is saved. */
    protected boolean modify(ShipmentCreationCheckRequest request, Predicate<T> change) {
        AtomicBoolean changed = new AtomicBoolean();
        optimisticLockingExecutor.modifyAndSave(
                () -> load(request.getStoreId(), request.getOwnerId()),
                (Consumer<T>) fresh -> changed.set(fresh != null && change.test(fresh)),
                fresh -> {
                    if (changed.get()) {
                        save(fresh);
                    }
                });
        return changed.get();
    }
}
