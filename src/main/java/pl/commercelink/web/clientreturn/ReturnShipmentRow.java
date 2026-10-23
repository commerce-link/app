package pl.commercelink.web.clientreturn;

import pl.commercelink.orders.ShipmentPickupWindow;

import java.time.LocalDateTime;

/**
 * One shipment on the public return page. typeKey names the shipment when the carrier is unknown; labelKey says whose
 * parcel it is (null for an entry typed by hand); stateKey and stateTone describe a creation still under way or in doubt.
 */
public record ReturnShipmentRow(String carrier, String typeKey, String labelKey, String trackingNo, String trackingUrl,
                                LocalDateTime shippedAt, LocalDateTime deliveredAt, ShipmentPickupWindow pickupWindow,
                                String stateKey, String stateTone) {
}
