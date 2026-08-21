package pl.commercelink.inventory.deliveries;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.orders.OrderItemsRepository;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DropshipOrderLocatorTest {

    @Mock
    private OrderItemsRepository orderItemsRepository;

    @InjectMocks
    private DropshipOrderLocator locator;

    private OrderItem itemOf(String orderId) {
        OrderItem item = new OrderItem();
        item.setOrderId(orderId);
        return item;
    }

    @Test
    void returnsTheSingleOrderIdBehindADelivery() {
        // given
        when(orderItemsRepository.findByClaimedDeliveryId("d-8f3a"))
                .thenReturn(List.of(itemOf("order-1"), itemOf("order-1")));

        // when / then
        assertEquals(Optional.of("order-1"), locator.locate("d-8f3a"));
    }

    @Test
    void returnsEmptyWhenIndexHasNoEntriesYet() {
        // given
        when(orderItemsRepository.findByClaimedDeliveryId("d-8f3a")).thenReturn(List.of());

        // when / then
        assertEquals(Optional.empty(), locator.locate("d-8f3a"));
    }

    @Test
    void twoDistinctOrdersViolateTheDropshipInvariant() {
        // given
        when(orderItemsRepository.findByClaimedDeliveryId("d-8f3a"))
                .thenReturn(List.of(itemOf("order-1"), itemOf("order-2")));

        // when / then
        assertThrows(IllegalStateException.class, () -> locator.locate("d-8f3a"));
    }
}
