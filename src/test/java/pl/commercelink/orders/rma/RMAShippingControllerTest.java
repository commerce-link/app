package pl.commercelink.orders.rma;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import pl.commercelink.orders.Shipment;
import pl.commercelink.orders.ShipmentCreationState;
import pl.commercelink.orders.ShipmentType;
import pl.commercelink.orders.ShippingForm;
import pl.commercelink.shipping.DeliveryTarget;
import pl.commercelink.stores.AuthorizedCarrier;
import pl.commercelink.stores.RMAConfiguration;
import pl.commercelink.stores.Store;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RMAShippingControllerTest {

    @Mock private RMARepository rmaRepository;

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
        assertEquals(new DeliveryTarget(null, null, null), controllerFor(storeWith(configuration)).resolveDeliveryTarget(null));
    }

    @Test
    void returnsNothingWhenTheStoreHasNoReturnsConfiguration() {
        // when / then
        assertEquals(new DeliveryTarget(null, null, null), controllerFor(storeWith(null)).resolveDeliveryTarget(null));
    }

    @Test
    void anRmaWhoseShipmentIsBeingCreatedCannotBookAnother() {
        // given
        Shipment creating = new Shipment(ShipmentType.Courier);
        creating.setCreation(ShipmentCreationState.pending("cmd-1", LocalDateTime.now()));
        RMA rma = new RMA("store-1");
        rma.setRmaId("rma-1");
        rma.setShipments(new ArrayList<>(List.of(creating)));
        when(rmaRepository.findById("store-1", "rma-1")).thenReturn(rma);
        RMAShippingController controller = controllerInStore();

        // when / then
        assertEquals("shipping.error.creating", controller.refuseBooking(new ShippingForm("rma-1", "rma")));
    }

    @Test
    void anRmaWithoutAShipmentBeingCreatedCanBook() {
        // given
        RMA rma = new RMA("store-1");
        rma.setRmaId("rma-1");
        when(rmaRepository.findById("store-1", "rma-1")).thenReturn(rma);
        RMAShippingController controller = controllerInStore();

        // when / then
        assertNull(controller.refuseBooking(new ShippingForm("rma-1", "rma")));
    }

    private RMAShippingController controllerInStore() {
        RMAShippingController controller = new RMAShippingController() {
            @Override
            protected String getStoreId() {
                return "store-1";
            }
        };
        ReflectionTestUtils.setField(controller, "rmaRepository", rmaRepository);
        return controller;
    }
}
