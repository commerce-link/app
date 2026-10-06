package pl.commercelink.shipping;

import org.springframework.stereotype.Component;
import pl.commercelink.orders.Shipment;
import pl.commercelink.orders.rma.RMA;
import pl.commercelink.orders.rma.RMAItem;
import pl.commercelink.orders.rma.RMAItemsRepository;
import pl.commercelink.orders.rma.RMALifecycle;
import pl.commercelink.orders.rma.RMARepository;
import pl.commercelink.starter.dynamodb.OptimisticLockingExecutor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@Component
public class RmaShipmentOwner extends StoredShipmentOwner<RMA> {

    private final RMARepository rmaRepository;
    private final RMAItemsRepository rmaItemsRepository;
    private final RMALifecycle rmaLifecycle;
    private final ShipmentTrackingSubscriber trackingSubscriber;

    public RmaShipmentOwner(RMARepository rmaRepository, OptimisticLockingExecutor optimisticLockingExecutor,
                            RMAItemsRepository rmaItemsRepository, RMALifecycle rmaLifecycle,
                            ShipmentTrackingSubscriber trackingSubscriber) {
        super(optimisticLockingExecutor);
        this.rmaRepository = rmaRepository;
        this.rmaItemsRepository = rmaItemsRepository;
        this.rmaLifecycle = rmaLifecycle;
        this.trackingSubscriber = trackingSubscriber;
    }

    @Override
    public ShipmentOwnerType type() {
        return ShipmentOwnerType.RMA;
    }

    /**
     * An RMA shipment takes the place of the ones {@link #keepsItsPlace} lets go, unless one is still being created; a
     * shipment already created (e.g. still waiting for its courier) stays.
     */
    @Override
    public boolean markCreating(ShipmentCreationCheckRequest request, Shipment placeholder) {
        return modify(request, rma -> {
            // a second command next to one in flight would pay for a second label
            if (rma.getShipments().stream().anyMatch(Shipment::isCreating)) {
                return false;
            }
            List<Shipment> next = new ArrayList<>(rma.getShipments().stream()
                    .filter(StoredShipmentOwner::keepsItsPlace).toList());
            next.add(placeholder);
            rma.setShipments(next);
            return true;
        });
    }

    /** The operator's shipment: the items go to the customer or to repair, and the RMA moves on. */
    @Override
    protected void afterCreated(ShipmentCreationCheckRequest request) {
        RMA rma = rmaRepository.findById(request.getStoreId(), request.getOwnerId());
        List<String> ids = request.getItemIds() == null ? List.of() : request.getItemIds();
        List<RMAItem> items = rmaItemsRepository.findByRmaId(rma.getRmaId()).stream()
                .filter(item -> ids.contains(item.getItemId()))
                .toList();
        Consumer<RMAItem> statusUpdater = request.isToClient() ? RMAItem::markAsReturnedToClient : RMAItem::markAsSendToRepair;
        items.forEach(statusUpdater);
        rmaItemsRepository.batchSave(items);
        trackingSubscriber.subscribe(request.getStoreId(), rma);
        rmaLifecycle.update(rma, items);
    }

    @Override
    protected RMA load(String storeId, String ownerId) {
        return rmaRepository.findById(storeId, ownerId);
    }

    @Override
    protected void save(RMA rma) {
        rmaRepository.save(rma);
    }

    @Override
    protected List<Shipment> shipments(RMA rma) {
        return rma.getShipments();
    }
}
