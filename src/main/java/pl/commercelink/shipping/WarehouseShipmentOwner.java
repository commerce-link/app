package pl.commercelink.shipping;

import org.springframework.stereotype.Component;
import pl.commercelink.orders.Shipment;

@Component
public class WarehouseShipmentOwner implements ShipmentOwner {

    @Override
    public ShipmentOwnerType type() {
        return ShipmentOwnerType.WAREHOUSE;
    }

    @Override
    public boolean markCreating(ShipmentCreationCheckRequest request, Shipment placeholder) {
        // warehouse shipments are not stored: the check message carries all that settling needs
        return true;
    }

    @Override
    public void recordExternalId(ShipmentCreationCheckRequest request) {
        // nothing stored
    }

    @Override
    public void refused(ShipmentCreationCheckRequest request, String error) {
        // the operator sees the reason on the shipping page
    }
}
