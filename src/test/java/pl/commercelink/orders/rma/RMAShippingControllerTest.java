package pl.commercelink.orders.rma;

import org.junit.jupiter.api.Test;
import pl.commercelink.stores.AuthorizedCarrier;
import pl.commercelink.stores.RMAConfiguration;
import pl.commercelink.stores.Store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RMAShippingControllerTest {

    private RMAShippingController controllerFor(Store store) {
        return new RMAShippingController() {
            @Override
            protected Store getStore() {
                return store;
            }
        };
    }

    private Store storeWith(RMAConfiguration rmaConfiguration) {
        Store store = new Store();
        store.setRmaConfiguration(rmaConfiguration);
        return store;
    }

    @Test
    void returnsTheCarrierConfiguredForReturns() {
        // given
        RMAConfiguration configuration = new RMAConfiguration();
        configuration.setCarrier(new AuthorizedCarrier("12", "inpost", "InPost Paczkomaty"));

        // when
        String carrier = controllerFor(storeWith(configuration)).resolveDeliveryCarrier(null);

        // then
        assertEquals("inpost", carrier);
    }

    @Test
    void returnsNothingWhenNoCarrierIsConfigured() {
        // given
        RMAConfiguration configuration = new RMAConfiguration();

        // when / then
        assertNull(controllerFor(storeWith(configuration)).resolveDeliveryCarrier(null));
    }

    @Test
    void returnsNothingWhenTheStoreHasNoReturnsConfiguration() {
        // when / then
        assertNull(controllerFor(storeWith(null)).resolveDeliveryCarrier(null));
    }
}
