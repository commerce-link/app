package pl.commercelink.orders;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pl.commercelink.inventory.deliveries.DropshipItemLookup;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/** B2: every bulk action says how many of the checked items it changed. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrdersManagerBulkResultTest {

    private static final String STORE_ID = "store-1";
    private static final String ORDER_ID = "order-1";

    @Mock private OrdersRepository ordersRepository;
    @Mock private OrderItemsRepository orderItemsRepository;
    @Mock private OrderLifecycle orderLifecycle;
    @Mock private DropshipItemLookup dropshipItemLookup;

    @InjectMocks
    private OrdersManager ordersManager;

    private OrderItem item(String id, FulfilmentStatus status, boolean withAllocation) {
        OrderItem item = new OrderItem(ORDER_ID, "CPU", "Ryzen", 1, 100, "MFN", false);
        item.setItemId(id);
        item.setStatus(status);
        if (withAllocation) {
            item.setEan("590");
            item.setManufacturerCode("MFN");
            item.setDeliveryId("Acme");
        }
        return item;
    }

    private void given(OrderItem... items) {
        Order order = new Order(STORE_ID);
        order.setOrderId(ORDER_ID);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
        when(orderItemsRepository.findByOrderId(ORDER_ID)).thenReturn(new java.util.ArrayList<>(List.of(items)));
        when(dropshipItemLookup.itemIdsInDropshipDeliveries(anyString(), any())).thenReturn(Set.of());
    }

    @Test
    void allocationCountsOnlyItemsReadyForIt() {
        // given
        given(item("ready", FulfilmentStatus.New, true), item("bare", FulfilmentStatus.New, false),
                item("ordered", FulfilmentStatus.Ordered, true));

        // when
        OrdersManager.Result result = ordersManager.moveItemsToAllocation(STORE_ID, ORDER_ID, List.of("ready", "bare", "ordered"));

        // then
        assertThat(result.getChanged()).isEqualTo(1);
        assertThat(result.getRequested()).isEqualTo(3);
    }

    @Test
    void removalCountsNewItemsAndServicesOnly() {
        // given
        OrderItem service = item("service", FulfilmentStatus.Delivered, false);
        service.setService(true);
        given(item("new", FulfilmentStatus.New, false), service, item("ordered", FulfilmentStatus.Ordered, true));

        // when
        OrdersManager.Result result = ordersManager.removeFromOrder(STORE_ID, ORDER_ID, List.of("new", "service", "ordered"));

        // then
        assertThat(result.getChanged()).isEqualTo(2);
        assertThat(result.getRequested()).isEqualTo(3);
    }

    @Test
    void dropshipItemsAreSkippedAndCountedSeparately() {
        // given
        given(item("d1", FulfilmentStatus.Ordered, true), item("w1", FulfilmentStatus.New, false));
        when(dropshipItemLookup.itemIdsInDropshipDeliveries(anyString(), any())).thenReturn(Set.of("d1"));

        // when
        OrdersManager.Result result = ordersManager.moveOrderItemsToTheWarehouse(STORE_ID, ORDER_ID, List.of("d1", "w1"));

        // then
        assertThat(result.getSkippedDropshipItems()).isEqualTo(1);
        assertThat(result.getChanged()).isZero();
        assertThat(result.getRequested()).isEqualTo(2);
    }
}
