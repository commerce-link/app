package pl.commercelink.orders;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.shipping.CarrierDictionary;
import pl.commercelink.stores.IntegrationType;
import pl.commercelink.stores.Store;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShipmentCarrierOptionsTest {

    @Mock
    private CarrierDictionary carrierDictionary;
    @Mock
    private Store store;

    @InjectMocks
    private ShipmentCarrierOptions options;

    private static Order orderShippedBy(String carrier) {
        Order order = new Order("store-1");
        Shipment shipment = new Shipment();
        shipment.setCarrier(carrier);
        order.addShipment(shipment);
        return order;
    }

    @Test
    void combinesDictionaryCarriersWithCarriersAlreadyUsedByTheOrder() {
        // given
        when(store.getConfigurationValue(IntegrationType.SHIPPING_PROVIDER)).thenReturn("furgonetka");
        when(carrierDictionary.namesUsedBy("furgonetka")).thenReturn(Set.of("DPD"));

        // when / then
        assertThat(options.forOrder(orderShippedBy("InPost"), store)).containsExactlyInAnyOrder("DPD", "InPost");
    }

    @Test
    void returnsNoOptionsWhenTheStoreHasNoShippingIntegration() {
        // given
        when(store.getConfigurationValue(IntegrationType.SHIPPING_PROVIDER)).thenReturn(null);
        when(carrierDictionary.namesUsedBy(null)).thenReturn(Set.of());

        // when / then
        assertThat(options.forOrder(orderShippedBy("InPost"), store)).isEmpty();
    }
}
