package pl.commercelink.shipping;

import org.springframework.stereotype.Component;
import pl.commercelink.orders.rma.RMAItemsRepository;
import pl.commercelink.orders.rma.RMALifecycle;
import pl.commercelink.orders.rma.RMARepository;
import pl.commercelink.starter.dynamodb.OptimisticLockingExecutor;

@Component
public class RmaReturnShipmentOwner extends RmaShipmentOwner {

    public RmaReturnShipmentOwner(RMARepository rmaRepository, OptimisticLockingExecutor optimisticLockingExecutor,
                                  RMAItemsRepository rmaItemsRepository, RMALifecycle rmaLifecycle,
                                  ShipmentTrackingSubscriber trackingSubscriber) {
        super(rmaRepository, optimisticLockingExecutor, rmaItemsRepository, rmaLifecycle, trackingSubscriber);
    }

    @Override
    public ShipmentOwnerType type() {
        return ShipmentOwnerType.RMA_RETURN;
    }

    @Override
    public void refused(ShipmentCreationCheckRequest request, String error) {
        // the customer sees the reason on the return page and submits again: nothing waits on the RMA
        modify(request, rma -> rma.getShipments().removeIf(s -> s.isCreationPendingFor(request.getCommandId())));
    }

    @Override
    protected void afterCreated(ShipmentCreationCheckRequest request) {
        // the customer's items change status when the goods arrive, as before; the e-mail follows the pickup
    }
}
