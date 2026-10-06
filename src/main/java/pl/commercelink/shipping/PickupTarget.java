package pl.commercelink.shipping;

/** One package of a pickup command and the owner whose shipments carry it. */
public record PickupTarget(ShipmentOwnerType ownerType, String ownerId, String externalId, String trackingNo) {
}
