package pl.commercelink.shipping;

import pl.commercelink.orders.Shipment;
import pl.commercelink.orders.ShipmentPickup;
import pl.commercelink.orders.ShipmentType;
import pl.commercelink.shipping.api.ShipmentResult;

import java.time.LocalDateTime;
import java.util.List;

/**
 * The shipments a created command stands for: one per parcel. The delivery choice (type, point, carrier the provider
 * did not name) is the placeholder's, which only the owner's record holds (StoredShipmentOwner#succeeded).
 */
public final class ShipmentResults {

    private ShipmentResults() {
    }

    public static List<Shipment> toShipments(ShipmentResult result, String provider, String pickUpAddressId) {
        LocalDateTime now = LocalDateTime.now();
        return result.parcels().stream().map(parcel -> {
            Shipment s = new Shipment(ShipmentType.Courier);
            s.setProvider(provider);
            s.setPickUpAddressId(pickUpAddressId);
            s.setExternalId(result.externalId());
            s.setTrackingNo(parcel.trackingNo());
            s.setCarrier(parcel.carrier());
            s.setTrackingUrl(parcel.trackingUrl());
            s.setShippedAt(now);
            s.setPickup(pickupOf(parcel));
            return s;
        }).toList();
    }

    private static ShipmentPickup pickupOf(ShipmentResult.ShipmentParcelResult parcel) {
        if (parcel.pickupNumber() != null) {
            return ShipmentPickup.bookedByCarrier(parcel.pickupNumber());
        }
        return parcel.pickupRequired() ? ShipmentPickup.awaiting() : ShipmentPickup.notRequired();
    }
}
