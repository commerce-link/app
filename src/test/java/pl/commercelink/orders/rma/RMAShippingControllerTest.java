package pl.commercelink.orders.rma;

import org.junit.jupiter.api.Test;
import pl.commercelink.shipping.DeliveryTarget;
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
        DeliveryTarget target = controllerFor(storeWith(configuration)).resolveDeliveryTarget(null);

        // then
        assertEquals("inpost", target.carrier());
        assertNull(target.pointCode());
    }

    @Test
    void returnsNothingWhenNoCarrierIsConfigured() {
        // given
        RMAConfiguration configuration = new RMAConfiguration();

        // when / then
        assertEquals(new DeliveryTarget(null, null), controllerFor(storeWith(configuration)).resolveDeliveryTarget(null));
    }

    @Test
    void returnsNothingWhenTheStoreHasNoReturnsConfiguration() {
        // when / then
        assertEquals(new DeliveryTarget(null, null), controllerFor(storeWith(null)).resolveDeliveryTarget(null));
    }
}
