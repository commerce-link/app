package pl.commercelink.inventory.deliveries;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeliveredPredicateTest {

    @Mock
    private DeliveriesRepository deliveriesRepository;

    @InjectMocks
    private DeliveredPredicate predicate;

    private static Delivered from(String deliveryId) {
        return () -> deliveryId;
    }

    @Test
    void firstWithoutDeliveryIsTheItemWhoseDeliveryIsNoRecordOfTheStore() {
        // given
        Delivered received = from("delivery-1");
        Delivered addedByHand = from("Unknown");
        when(deliveriesRepository.findById("store-1", "delivery-1")).thenReturn(new Delivery("store-1", null, "Acme"));
        when(deliveriesRepository.findById("store-1", "Unknown")).thenReturn(null);

        // when / then
        assertThat(predicate.firstWithoutDelivery("store-1", List.of(received, addedByHand))).contains(addedByHand);
    }

    @Test
    void itemsFromDeliveriesOfTheStoreHaveNoneWithoutDelivery() {
        // given
        when(deliveriesRepository.findById("store-1", "delivery-1")).thenReturn(new Delivery("store-1", null, "Acme"));

        // when / then
        assertThat(predicate.firstWithoutDelivery("store-1", List.of(from("delivery-1"), from("delivery-1")))).isEmpty();
    }

    @Test
    void itemWithoutAnyDeliveryIdHasNoDelivery() {
        // given
        Delivered blank = from(null);

        // when / then
        assertThat(predicate.firstWithoutDelivery("store-1", List.of(blank))).contains(blank);
    }
}
