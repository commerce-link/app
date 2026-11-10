package pl.commercelink.orders;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.carriers.CarriersRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShipmentCarrierOptionsTest {

    @Mock
    private CarriersRepository carriersRepository;

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
    void combinesOurCarriersWithTextsAlreadyUsedByTheOrder() {
        // given
        when(carriersRepository.names()).thenReturn(List.of("DPD", "InPost"));

        // when / then
        assertThat(options.forOrder(orderShippedBy("my"))).containsExactly("DPD", "InPost", "my");
    }

    @Test
    void returnsNoOptionsWithoutCarriersSoTheFormFallsBackToText() {
        // given
        when(carriersRepository.names()).thenReturn(List.of());

        // when / then
        assertThat(options.forOrder(orderShippedBy("InPost"))).isEmpty();
    }
}
