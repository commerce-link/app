package pl.commercelink.shipping;

import org.springframework.stereotype.Component;
import pl.commercelink.orders.Shipment;
import pl.commercelink.orders.ShipmentPickup;

import java.util.Collection;
import java.util.List;
import java.util.function.UnaryOperator;

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

    @Override
    public boolean awaits(ShipmentCreationCheckRequest request) {
        // nothing stored to wait on: the message itself is the only record of the command
        return true;
    }

    @Override
    public boolean succeeded(ShipmentCreationCheckRequest request, List<Shipment> created) {
        return true;
    }

    @Override
    public void failed(ShipmentCreationCheckRequest request, String error, String errorKey) {
        // nothing stored
    }

    @Override
    public boolean awaitsPickup(String storeId, String ownerId, String externalId) {
        // the warehouse orders its pickup right away and never lists its packages in the pickup index
        return false;
    }

    @Override
    public int applyPickup(String storeId, String ownerId, Collection<String> externalIds,
                           UnaryOperator<ShipmentPickup> change) {
        // nothing stored: counted as applied, so ordering and settling the pickup go on
        return externalIds.size();
    }
}
