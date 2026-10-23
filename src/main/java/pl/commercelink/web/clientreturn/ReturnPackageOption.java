package pl.commercelink.web.clientreturn;

/** A package the customer can send goods back in; parcels is a ready-to-show description or null. */
public record ReturnPackageOption(String id, String title, int parcelCount, String parcels, boolean isDefault) {
}
