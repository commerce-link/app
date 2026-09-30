package pl.commercelink.orders.notifications;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderLifecycle;
import pl.commercelink.orders.OrderStatus;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoreActivity;
import pl.commercelink.stores.StoresRepository;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderLifecycleCronTest {

    @Mock private StoresRepository storesRepository;
    @Mock private OrdersRepository ordersRepository;
    @Mock private OrderLifecycle orderLifecycle;
    @Mock private StoreActivity storeActivity;

    @InjectMocks
    private OrderLifecycleCron cron;

    private static Store store(String storeId) {
        Store store = new Store();
        store.setStoreId(storeId);
        return store;
    }

    @Test
    void updatesOrdersOfActiveStoresOnly() {
        // given
        Store active = store("active");
        Store inactive = store("inactive");
        Order order = new Order();
        when(storesRepository.findAll()).thenReturn(List.of(active, inactive));
        when(storeActivity.isActive(active)).thenReturn(true);
        when(storeActivity.isActive(inactive)).thenReturn(false);
        when(ordersRepository.findAllByStoreIdAndStatus("active", OrderStatus.Shipping, OrderStatus.Delivered))
                .thenReturn(List.of(order));

        // when
        cron.processDeliveredOrders("tick");

        // then
        verify(orderLifecycle).update(order);
        verify(ordersRepository, never()).findAllByStoreIdAndStatus(eq("inactive"), eq(OrderStatus.Shipping),
                eq(OrderStatus.Delivered));
    }
}
