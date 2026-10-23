package pl.commercelink.shipping;

import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class ShipmentOwners {

    private final Map<ShipmentOwnerType, ShipmentOwner> byType = new EnumMap<>(ShipmentOwnerType.class);

    public ShipmentOwners(List<ShipmentOwner> owners) {
        owners.forEach(owner -> byType.put(owner.type(), owner));
    }

    public ShipmentOwner get(ShipmentOwnerType type) {
        ShipmentOwner owner = byType.get(type);
        if (owner == null) {
            throw new IllegalStateException("No shipment owner for " + type);
        }
        return owner;
    }
}
