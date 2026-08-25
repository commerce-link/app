package pl.commercelink.inventory.deliveries;

import org.apache.commons.lang3.StringUtils;
import pl.commercelink.orders.Shipment;
import pl.commercelink.orders.ShipmentType;

import java.time.LocalDateTime;

public record DropshipShipment(ShipmentType type, String carrier, String trackingNo,
                               String collectionPointCode, LocalDateTime shippedAt) {

    public String validationError() {
        if (type != ShipmentType.Courier && type != ShipmentType.PickupPoint) {
            return "deliveries.dropship.shipment.error.type";
        }
        if (StringUtils.isBlank(carrier)) {
            return "deliveries.dropship.shipment.error.carrier";
        }
        if (StringUtils.isBlank(trackingNo)) {
            return "deliveries.dropship.shipment.error.trackingNo";
        }
        if (type == ShipmentType.PickupPoint && StringUtils.isBlank(collectionPointCode)) {
            return "deliveries.dropship.shipment.error.collectionPoint";
        }
        if (shippedAt == null) {
            return "deliveries.dropship.shipment.error.shippedAt";
        }
        return null;
    }

    public void applyTo(Shipment shipment) {
        shipment.setType(type);
        shipment.setCarrier(StringUtils.trimToNull(carrier));
        shipment.setTrackingNo(StringUtils.trimToNull(trackingNo));
        shipment.setCollectionPointCode(type == ShipmentType.PickupPoint ? StringUtils.trimToNull(collectionPointCode) : null);
        shipment.setShippedAt(shippedAt);
    }
}
