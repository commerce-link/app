package pl.commercelink.shipping;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.commercelink.orders.Shipment;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class AwaitingPickupIndex {

    private final AwaitingPickupsRepository repository;

    /** One entry per package (every parcel row of a package carries its externalId) that waits for a pickup. */
    public void add(String storeId, ShipmentOwnerType ownerType, String ownerId, List<Shipment> shipments) {
        Map<String, AwaitingPickup> entries = new LinkedHashMap<>();
        for (Shipment s : shipments) {
            if (!s.awaitsPickup() || s.getExternalId() == null || s.getProvider() == null || s.getPickUpAddressId() == null) {
                continue;
            }
            entries.computeIfAbsent(s.getExternalId(), id -> {
                AwaitingPickup entry = new AwaitingPickup();
                entry.setStoreId(storeId);
                entry.setExternalId(id);
                entry.setProvider(s.getProvider());
                entry.setCarrier(s.getCarrier());
                entry.setPickUpAddressId(s.getPickUpAddressId());
                entry.setOwnerType(ownerType);
                entry.setOwnerId(ownerId);
                entry.setTrackingNo(s.getTrackingNo());
                entry.setCreatedAt(LocalDateTime.now());
                return entry;
            });
        }
        // An empty batch would still cost a mapper round trip for nothing.
        if (!entries.isEmpty()) {
            repository.batchSave(List.copyOf(entries.values()));
        }
    }

    public void remove(String storeId, Collection<String> externalIds) {
        externalIds.stream().distinct().forEach(id -> repository.delete(storeId, id));
    }

    public List<AwaitingPickup> list(String storeId) {
        return repository.findByStore(storeId);
    }
}
