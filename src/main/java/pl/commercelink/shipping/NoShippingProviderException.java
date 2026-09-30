package pl.commercelink.shipping;

import pl.commercelink.shipping.api.ShippingException;

/**
 * The store has no shipping provider (none configured, or its authorization was lost): nothing can be sent to a
 * carrier. A type of its own so the order page can say it in the operator's language.
 */
public class NoShippingProviderException extends ShippingException {

    public NoShippingProviderException() {
        super("No shipping provider configured for the store");
    }
}
