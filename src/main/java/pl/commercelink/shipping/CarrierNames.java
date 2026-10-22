package pl.commercelink.shipping;

import org.apache.commons.lang3.StringUtils;
import pl.commercelink.orders.Shipment;

import java.util.Locale;
import java.util.Map;

/** The carrier name a customer reads on the public order page; carrier codes are stored as the integrations deliver them. */
public final class CarrierNames {

    private static final Map<String, String> KNOWN = Map.ofEntries(
            Map.entry("dhl", "DHL"),
            Map.entry("dpd", "DPD"),
            Map.entry("inpost", "InPost"),
            Map.entry("inpostpaczkakurierska", "InPost Kurier"),
            Map.entry("gls", "GLS"),
            Map.entry("ups", "UPS"),
            Map.entry("fedex", "FedEx"),
            Map.entry("poczta", "Poczta Polska"),
            Map.entry("pocztapolska", "Poczta Polska"),
            Map.entry("orlen", "ORLEN Paczka"));

    private CarrierNames() {
    }

    public static String forCustomer(Shipment shipment) {
        if (ShippingIntegrationChoice.ALLEGRO.equals(shipment.getProvider())) {
            return AllegroCarrierNames.displayName(shipment.getCarrier());
        }
        String carrier = shipment.getCarrier();
        if (StringUtils.isBlank(carrier)) {
            return null;
        }
        return KNOWN.getOrDefault(carrier.trim().toLowerCase(Locale.ROOT), carrier);
    }
}
