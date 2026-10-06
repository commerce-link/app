package pl.commercelink.shipping;

import pl.commercelink.orders.Shipment;
import pl.commercelink.orders.ShipmentPickup;
import pl.commercelink.shipping.api.ShipmentResult;

import java.time.LocalDateTime;
import java.util.List;

/** The shipments a created command stands for: one per parcel, with the placeholder's delivery choice. */
public final class ShipmentResults {

    private ShipmentResults() {
    }

    public static List<Shipment> toShipments(ShipmentResult result, Shipment placeholder) {
        LocalDateTime now = LocalDateTime.now();
        return result.parcels().stream().map(parcel -> {
            Shipment s = new Shipment(placeholder.getType());
            s.setCollectionPointCode(placeholder.getCollectionPointCode());
            s.setProvider(placeholder.getProvider());
            s.setPickUpAddressId(placeholder.getPickUpAddressId());
            s.setExternalId(result.externalId());
            s.setTrackingNo(parcel.trackingNo());
            s.setCarrier(parcel.carrier() != null ? parcel.carrier() : placeholder.getCarrier());
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
