package pl.commercelink.shipping;

import org.springframework.web.util.UriComponentsBuilder;
import pl.commercelink.orders.Shipment;

import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/** The addresses of "Pobierz etykietę" and "Zamów odbiór" on the order and RMA pages, with the way back to them. */
public final class ShipmentLinks {

    private ShipmentLinks() {
    }

    public static String label(String provider, String externalId, String back) {
        return UriComponentsBuilder.fromPath("/dashboard/shipping/labels/{provider}/{externalId}")
                .queryParam("back", back)
                .buildAndExpand(provider, externalId).encode().toUriString();
    }

    /** A created package of an integration: the shipment a label can be offered for. */
    public static boolean hasPackage(Shipment s) {
        return s.getProvider() != null && s.getExternalId() != null && s.getCreation() == null;
    }

    /**
     * The integrations of the packages among these shipments that hand out their labels, each asked once and only when
     * a package could carry the link (ShippingService#supportsLabels loads the account).
     */
    public static Set<String> labelProviders(List<Shipment> shipments, Predicate<String> supportsLabels) {
        return shipments.stream().filter(ShipmentLinks::hasPackage).map(Shipment::getProvider).distinct()
                .filter(supportsLabels)
                .collect(Collectors.toSet());
    }

    /**
     * The pickup page preset to the group of the first package waiting for a courier, or null when none waits. A
     * package without a pickup address (a customer's return, picked up at the customer's) is never in that group list.
     */
    public static String pickup(List<Shipment> shipments, String back) {
        return shipments.stream().filter(ShipmentLinks::listedForPickup).findFirst()
                .map(s -> UriComponentsBuilder.fromPath(ShipmentPickupController.PAGE)
                        .queryParam("group", "{group}")
                        .queryParam("back", back)
                        .buildAndExpand(PickupGroup.key(s.getProvider(), s.getCarrier(), s.getPickUpAddressId()))
                        .encode().toUriString())
                .orElse(null);
    }

    /** The pickup page on its first group, returning to back (a list address with its own query). */
    public static String pickupPage(String back) {
        // encoded as a variable, so the list's own "&" and "=" stay inside the back parameter
        return UriComponentsBuilder.fromPath(ShipmentPickupController.PAGE)
                .queryParam("back", "{back}")
                .encode()
                .buildAndExpand(back)
                .toUriString();
    }

    /** The package waits for "Zamów odbiór" among the store's other packages (PickupCandidates lists exactly these). */
    public static boolean listedForPickup(Shipment s) {
        return s.awaitsPickup() && s.getProvider() != null && s.getExternalId() != null && s.getPickUpAddressId() != null;
    }
}
