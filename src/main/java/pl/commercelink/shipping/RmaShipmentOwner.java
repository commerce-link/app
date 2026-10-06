package pl.commercelink.shipping;

import org.springframework.stereotype.Component;
import pl.commercelink.orders.Shipment;
import pl.commercelink.orders.rma.RMA;
import pl.commercelink.orders.rma.RMARepository;
import pl.commercelink.starter.dynamodb.OptimisticLockingExecutor;

import java.util.ArrayList;
import java.util.List;

@Component
public class RmaShipmentOwner extends StoredShipmentOwner<RMA> {

    private final RMARepository rmaRepository;

    public RmaShipmentOwner(RMARepository rmaRepository, OptimisticLockingExecutor optimisticLockingExecutor) {
        super(optimisticLockingExecutor);
        this.rmaRepository = rmaRepository;
    }

    @Override
    public ShipmentOwnerType type() {
        return ShipmentOwnerType.RMA;
    }

    /** An RMA shipment replaces the earlier ones, as booking it always did, unless one is still being created. */
    @Override
    public boolean markCreating(ShipmentCreationCheckRequest request, Shipment placeholder) {
        return modify(request, rma -> {
            // replacing the list would drop the first command's placeholder, and with it a paid label
            if (rma.getShipments().stream().anyMatch(Shipment::isCreating)) {
                return false;
            }
            rma.setShipments(new ArrayList<>(List.of(placeholder)));
            return true;
        });
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
