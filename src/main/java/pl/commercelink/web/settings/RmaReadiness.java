package pl.commercelink.web.settings;

import java.util.List;

/**
 * What a customer return needs besides this page, all of it set on the shipping page. The customer's return link
 * answers 404 without a carrier; without a template named {@code RMA - …} the customer has no package size to pick; and
 * without a default goods-receiving address the shipment cannot be created. None of the three was visible from here.
 *
 * @param carrierLabel      the saved carrier's name, or null when returns have none
 * @param returnTemplates   names of the package templates offered to the customer
 * @param receivingAddress  the default goods-receiving address in one line, or null when none is marked default
 * @param shippingHref      the shipping settings page, where the missing pieces are set
 */
public record RmaReadiness(String carrierLabel, boolean carrierAuthorized, List<String> returnTemplates,
                           String receivingAddress, String shippingHref) {

    public boolean carrierReady() {
        return carrierLabel != null && carrierAuthorized;
    }

    public boolean templatesReady() {
        return !returnTemplates.isEmpty();
    }

    public boolean addressReady() {
        return receivingAddress != null;
    }

    public boolean ready() {
        return carrierReady() && templatesReady() && addressReady();
    }

    /**
     * Whether customer returns fail. A carrier removed from the authorised list does not block them: shipments are still
     * created with the saved copy's id, so it only needs attention.
     */
    public boolean blocked() {
        return carrierLabel == null || !templatesReady() || !addressReady();
    }
}
