package pl.commercelink.shipping;

import pl.commercelink.shipping.api.ShipmentProposal;

/**
 * One integration offered under "Wyślij przez" for an order. An unavailable one is shown greyed with its reason
 * (reasonKey, with reasonDetail as its argument when the integration said it in its own words). proposal: what
 * Wysyłam z Allegro proposed for the order (method, point, limits), null for every other integration.
 */
public record ShippingIntegrationOption(String name, String displayName, boolean available, String reasonKey,
                                        String reasonDetail, boolean suggested, ShipmentProposal proposal) {

    public static ShippingIntegrationOption available(String name, String displayName, ShipmentProposal proposal) {
        return new ShippingIntegrationOption(name, displayName, true, null, null, false, proposal);
    }

    public static ShippingIntegrationOption unavailable(String name, String displayName, String reasonKey,
                                                        String reasonDetail) {
        return new ShippingIntegrationOption(name, displayName, false, reasonKey, reasonDetail, false, null);
    }

    public ShippingIntegrationOption suggestedCopy() {
        return new ShippingIntegrationOption(name, displayName, available, reasonKey, reasonDetail, true, proposal);
    }

    public boolean isAllegro() {
        return ShippingIntegrationChoice.ALLEGRO.equals(name);
    }
}
