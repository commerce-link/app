package pl.commercelink.shipping;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import pl.commercelink.orders.Shipment;
import pl.commercelink.orders.ShipmentType;
import pl.commercelink.shipping.api.ShipmentResult;

import java.util.List;
import java.util.Set;

/** Writes the result of a creation command to its owner, for {@link ShipmentCreationChecker}. */
@Slf4j
@Component
@RequiredArgsConstructor
public class ShipmentCreationSettler {

    /** Owners whose packages wait for "Zamów odbiór" in the store's pickup index. */
    private static final Set<ShipmentOwnerType> INDEXED = Set.of(ShipmentOwnerType.ORDER, ShipmentOwnerType.RMA);

    private final ShipmentOwners owners;
    private final AwaitingPickupIndex index;

    public void succeeded(ShipmentCreationCheckRequest request, ShipmentResult result) {
        // type, delivery point and carrier come from the placeholder, which only the owner's record holds
        Shipment template = new Shipment(ShipmentType.Courier);
        template.setProvider(request.getProvider());
        template.setPickUpAddressId(request.getPickUpAddressId());
        List<Shipment> created = ShipmentResults.toShipments(result, template);
        if (!owners.get(request.getOwnerType()).succeeded(request, created)) {
            log.warn("Creation result dropped, nothing waits for it: store={} owner={} {} command={}",
                    request.getStoreId(), request.getOwnerType(), request.getOwnerId(), request.getCommandId());
            return;
        }
        if (!INDEXED.contains(request.getOwnerType())) {
            return;
        }
        try {
            index.add(request.getStoreId(), request.getOwnerType(), request.getOwnerId(), created);
        } catch (RuntimeException e) {
            // the shipment is already saved, so a redelivery would be dropped and could not add the row either
            log.error("Package {} of {} {} in store {} is missing from the pickup index", request.getExternalId(),
                    request.getOwnerType(), request.getOwnerId(), request.getStoreId(), e);
        }
    }

    public void failed(ShipmentCreationCheckRequest request, String error) {
        log.warn("Shipment creation failed store={} owner={} {} command={}: {}", request.getStoreId(),
                request.getOwnerType(), request.getOwnerId(), request.getCommandId(), error);
        owners.get(request.getOwnerType()).failed(request, error, null);
    }

    /** Our own reason (never confirmed, no provider): the provider may hold a paid label nobody knows of. */
    public void failedWithKey(ShipmentCreationCheckRequest request, String key) {
        log.error("Shipment creation ended without a result store={} owner={} {} command={} package={}: {}",
                request.getStoreId(), request.getOwnerType(), request.getOwnerId(), request.getCommandId(),
                request.getExternalId(), key);
        owners.get(request.getOwnerType()).failed(request, null, key);
    }
}
