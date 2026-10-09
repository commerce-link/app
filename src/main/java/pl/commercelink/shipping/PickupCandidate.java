package pl.commercelink.shipping;

/** A package listed on "Zamów odbiór": who holds its shipment and which courier group it belongs to. */
public record PickupCandidate(ShipmentOwnerType ownerType, String ownerId, String externalId, String trackingNo,
                              String provider, String carrier, String pickUpAddressId) {

    public String groupKey() {
        return PickupGroup.key(provider, carrier, pickUpAddressId);
    }

    public PickupTarget target() {
        return new PickupTarget(ownerType, ownerId, externalId, trackingNo);
    }
}
