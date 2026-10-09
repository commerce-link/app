package pl.commercelink.orders.fulfilment;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderIndexEntry;
import pl.commercelink.orders.OrderStatus;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.starter.dynamodb.QueryPageResult;
import pl.commercelink.stores.FulfilmentConfiguration;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the queue order: the store's warehouse orders first, as one group, even when a dropship order waits longer;
 * dropship orders one at a time, oldest first, once the warehouse group is gone or skipped.
 */
@ExtendWith(MockitoExtension.class)
class FulfilmentQueueTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 9, 12, 0);

    @Mock
    private StoresRepository storesRepository;
    @Mock
    private OrdersRepository ordersRepository;
    @InjectMocks
    private FulfilmentQueue queue;

    @Test
    void warehouseGroupComesBeforeAnOlderDropshipOrder() {
        // given
        // a dropship order "d1" waits 10 days, longer than both; the queue does not even look for it
        OrderIndexEntry w1 = order("s1", "w1", 5, FulfilmentType.WarehouseFulfilment);
        OrderIndexEntry w2 = order("s1", "w2", 1, FulfilmentType.WarehouseFulfilment);
        when(ordersRepository.findAllWarehouseFulfilmentOrder("s1")).thenReturn(List.of(w1, w2));

        // when
        List<OrderIndexEntry> group = queue.pickFulfilmentGroup("s1", List.of(), null);

        // then
        assertThat(group).extracting(OrderIndexEntry::getOrderId).containsExactly("w1", "w2");
        verify(ordersRepository, never()).findOldestOrdersWaitingForFulfilment(any(), any(), anyInt(), any());
    }

    @Test
    void skippedWarehouseGroupHandsOverToTheOldestDropshipOrder() {
        // given
        OrderIndexEntry dropship = order("s1", "d1", 10, FulfilmentType.DirectToConsumer);
        when(ordersRepository.findAllWarehouseFulfilmentOrder("s1")).thenReturn(List.of(
                order("s1", "w1", 5, FulfilmentType.WarehouseFulfilment)));
        when(ordersRepository.findOldestOrdersWaitingForFulfilment(eq("s1"), eq(List.of("w1")), anyInt(), any()))
                .thenReturn(new QueryPageResult<>(List.of(dropship), null));

        // when
        List<OrderIndexEntry> group = queue.pickFulfilmentGroup("s1", List.of("w1"), null);

        // then
        assertThat(group).extracting(OrderIndexEntry::getOrderId).containsExactly("d1");
    }

    @Test
    void warehouseOrdersTheFilterRejectsDoNotHoldTheDropshipOrdersBack() {
        // given
        OrderIndexEntry dropship = order("s1", "d1", 10, FulfilmentType.DirectToConsumer);
        when(ordersRepository.findAllWarehouseFulfilmentOrder("s1")).thenReturn(List.of(
                order("s1", "w1", 5, FulfilmentType.WarehouseFulfilment)));
        waiting("s1", dropship);
        Order dropshipOrder = orderOf(dropship);
        Order warehouseOrder = orderOf(order("s1", "w1", 5, FulfilmentType.WarehouseFulfilment));
        when(ordersRepository.findById("s1", "d1")).thenReturn(dropshipOrder);
        when(ordersRepository.findById("s1", "w1")).thenReturn(warehouseOrder);

        // when
        List<OrderIndexEntry> group = queue.pickFulfilmentGroup("s1", List.of(),
                order -> order.getFulfilmentType() == FulfilmentType.DirectToConsumer);

        // then
        assertThat(group).extracting(OrderIndexEntry::getOrderId).containsExactly("d1");
    }

    @Test
    void superAdminGetsTheWarehouseGroupOfTheStoreWhoseWarehouseOrderWaitsLongest() {
        // given
        twoStores();
        when(ordersRepository.findAllWarehouseFulfilmentOrder("s1")).thenReturn(List.of(
                order("s1", "a1", 3, FulfilmentType.WarehouseFulfilment)));
        when(ordersRepository.findAllWarehouseFulfilmentOrder("s2")).thenReturn(List.of(
                order("s2", "b1", 7, FulfilmentType.WarehouseFulfilment),
                order("s2", "b2", 2, FulfilmentType.WarehouseFulfilment)));

        // when
        List<OrderIndexEntry> group = queue.pickFulfilmentGroup(List.of(), null);

        // then
        assertThat(group).extracting(OrderIndexEntry::getOrderId).containsExactly("b1", "b2");
    }

    @Test
    void superAdminGetsTheOldestDropshipOrderWhenNoWarehouseOrderWaits() {
        // given
        twoStores();
        when(ordersRepository.findAllWarehouseFulfilmentOrder(any())).thenReturn(List.of());
        waiting("s1", order("s1", "d1", 4, FulfilmentType.DirectToConsumer));
        waiting("s2", order("s2", "d2", 9, FulfilmentType.DirectToConsumer));

        // when
        List<OrderIndexEntry> group = queue.pickFulfilmentGroup(List.of(), null);

        // then
        assertThat(group).extracting(OrderIndexEntry::getOrderId).containsExactly("d2");
    }

    private void twoStores() {
        Store first = store("s1");
        Store second = store("s2");
        when(storesRepository.findAll()).thenReturn(List.of(first, second));
    }

    private void waiting(String storeId, OrderIndexEntry... orders) {
        when(ordersRepository.findOldestOrdersWaitingForFulfilment(eq(storeId), any(), anyInt(), any()))
                .thenReturn(new QueryPageResult<>(List.of(orders), null));
    }

    private static Store store(String storeId) {
        FulfilmentConfiguration configuration = new FulfilmentConfiguration();
        configuration.setAutomatedFulfilment(true);
        Store store = new Store();
        store.setStoreId(storeId);
        store.setFulfilmentConfiguration(configuration);
        return store;
    }

    private static OrderIndexEntry order(String storeId, String orderId, int daysAgo, FulfilmentType type) {
        return new OrderIndexEntry(storeId, orderId, null, NOW.minusDays(daysAgo), OrderStatus.New, type);
    }

    private static Order orderOf(OrderIndexEntry entry) {
        Order order = new Order(entry.getStoreId());
        order.setOrderId(entry.getOrderId());
        order.setOrderedAt(entry.getOrderedAt());
        order.setFulfilmentType(entry.getFulfilmentType());
        return order;
    }
}
