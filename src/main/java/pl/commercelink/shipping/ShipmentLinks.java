package pl.commercelink.shipping;

import org.springframework.web.util.UriComponentsBuilder;
import pl.commercelink.orders.Shipment;

import java.util.List;

/** The addresses of "Pobierz etykietę" and "Zamów odbiór" on the order and RMA pages, with the way back to them. */
public final class ShipmentLinks {

    private ShipmentLinks() {
    }

    public static String label(String provider, String externalId, String back) {
        return UriComponentsBuilder.fromPath("/dashboard/shipping/labels/{provider}/{externalId}")
                .queryParam("back", back)
                .buildAndExpand(provider, externalId).encode().toUriString();
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

    /** The package waits for "Zamów odbiór" among the store's other packages (PickupCandidates lists exactly these). */
    public static boolean listedForPickup(Shipment s) {
        return s.awaitsPickup() && s.getProvider() != null && s.getExternalId() != null && s.getPickUpAddressId() != null;
    }
}
