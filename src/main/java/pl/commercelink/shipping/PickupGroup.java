package pl.commercelink.shipping;

import java.util.List;

/** Packages one courier can take: the same integration, carrier and pickup address. */
public record PickupGroup(String key, String provider, String carrier, String pickUpAddressId,
                          List<PickupCandidate> entries) {

    public static String key(String provider, String carrier, String pickUpAddressId) {
        return provider + "|" + carrier + "|" + pickUpAddressId;
    }
}
