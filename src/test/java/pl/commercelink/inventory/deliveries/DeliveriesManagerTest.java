package pl.commercelink.inventory.deliveries;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pl.commercelink.orders.FulfilmentStatus;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderItemsRepository;
import pl.commercelink.orders.OrderStatus;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.orders.notifications.OrderNotificationsEventPublisher;
import pl.commercelink.starter.dynamodb.OptimisticLockingExecutor;
import pl.commercelink.testsupport.OptimisticLockingExecutorMocks;
import pl.commercelink.warehouse.builtin.WarehouseAllocationsManager;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DeliveriesManagerTest {

    private static final String STORE_ID = "store-1";
    private static final String DELIVERY_ID = "delivery-1";
    private static final LocalDate ORIGINAL_DELIVERY_DATE = LocalDate.of(2026, 5, 1);
    private static final LocalDate NEW_DELIVERY_DATE = LocalDate.of(2026, 5, 10);

    @Mock
    private OrdersRepository ordersRepository;
    @Mock
    private OrderItemsRepository orderItemsRepository;
    @Mock
    private OrderNotificationsEventPublisher notificationEventPublisher;
    @Mock
    private DeliveriesRepository deliveriesRepository;
    @Mock
    private OrderAllocationsManager orderAllocationsManager;
    @Mock
    private WarehouseAllocationsManager warehouseAllocationsManager;
    @Mock
    private OptimisticLockingExecutor optimisticLockingExecutor;

    @InjectMocks
    private DeliveriesManager deliveriesManager;

    @BeforeEach
    void setupExecutorPassThrough() {
        when(optimisticLockingExecutor.modifyAndSave(any(), any(), any()))
                .thenAnswer(OptimisticLockingExecutorMocks.passThroughModifyAndSave());
    }

    @Test
    @DisplayName("updateDelivery propagates new estimated assembly date to all affected non-completed orders when delivery is delayed")
    void updateDeliveryUpdatesEstimatedAssemblyOnAffectedOrdersWhenDelayed() {
        // given
        Delivery existing = deliveryWith(ORIGINAL_DELIVERY_DATE);
        Delivery updated = deliveryWith(NEW_DELIVERY_DATE);
        Order order1 = orderWithAssemblyDate("order-1", ORIGINAL_DELIVERY_DATE, OrderStatus.New);
        Order order2 = orderWithAssemblyDate("order-2", ORIGINAL_DELIVERY_DATE, OrderStatus.Assembly);
        when(deliveriesRepository.findById(STORE_ID, DELIVERY_ID)).thenReturn(existing);
        when(orderItemsRepository.findByDeliveryIdAndStatuses(eq(DELIVERY_ID), eq(Collections.singletonList(FulfilmentStatus.Ordered))))
                .thenReturn(List.of("order-1", "order-2"));
        when(ordersRepository.findById(STORE_ID, "order-1")).thenReturn(order1);
        when(ordersRepository.findById(STORE_ID, "order-2")).thenReturn(order2);

        // when
        deliveriesManager.updateDelivery(updated);

        // then
        verify(ordersRepository).save(order1);
        verify(ordersRepository).save(order2);
    }

    @Test
    @DisplayName("updateDelivery skips Completed orders entirely from the assembly date update")
    void updateDeliverySkipsCompletedOrdersFromAssemblyUpdate() {
        // given
        Delivery existing = deliveryWith(ORIGINAL_DELIVERY_DATE);
        Delivery updated = deliveryWith(NEW_DELIVERY_DATE);
        Order completedOrder = orderWithAssemblyDate("order-completed", ORIGINAL_DELIVERY_DATE, OrderStatus.Completed);
        Order newOrder = orderWithAssemblyDate("order-new", ORIGINAL_DELIVERY_DATE, OrderStatus.New);
        when(deliveriesRepository.findById(STORE_ID, DELIVERY_ID)).thenReturn(existing);
        when(orderItemsRepository.findByDeliveryIdAndStatuses(eq(DELIVERY_ID), eq(Collections.singletonList(FulfilmentStatus.Ordered))))
                .thenReturn(List.of("order-completed", "order-new"));
        when(ordersRepository.findById(STORE_ID, "order-completed")).thenReturn(completedOrder);
        when(ordersRepository.findById(STORE_ID, "order-new")).thenReturn(newOrder);

        // when
        deliveriesManager.updateDelivery(updated);

        // then
        verify(ordersRepository).save(newOrder);
        verify(ordersRepository, never()).save(completedOrder);
        verify(notificationEventPublisher, never()).publishAssemblyDateChanged(eq(completedOrder), any());
    }

    @Test
    @DisplayName("updateDelivery does not publish assembly-date-changed notification when assembly date does not actually change")
    void updateDeliveryDoesNotPublishWhenAssemblyDateUnchanged() {
        // given
        Delivery existing = deliveryWith(ORIGINAL_DELIVERY_DATE);
        Delivery updated = deliveryWith(NEW_DELIVERY_DATE);
        // order has assembly date AFTER new delivery date — updateEstimatedAssemblyAt returns same date
        LocalDate laterAssembly = NEW_DELIVERY_DATE.plusDays(5);
        Order order = orderWithAssemblyDate("order-1", laterAssembly, OrderStatus.New);
        when(deliveriesRepository.findById(STORE_ID, DELIVERY_ID)).thenReturn(existing);
        when(orderItemsRepository.findByDeliveryIdAndStatuses(eq(DELIVERY_ID), eq(Collections.singletonList(FulfilmentStatus.Ordered))))
                .thenReturn(List.of("order-1"));
        when(ordersRepository.findById(STORE_ID, "order-1")).thenReturn(order);

        // when
        deliveriesManager.updateDelivery(updated);

        // then
        verify(notificationEventPublisher, never()).publishAssemblyDateChanged(any(), any());
    }

    @Test
    @DisplayName("updateDelivery publishes assembly-date-changed notification once with the original old date when date moves forward")
    void updateDeliveryPublishesAssemblyDateChangedNotificationWithOldDateOnce() {
        // given
        Delivery existing = deliveryWith(ORIGINAL_DELIVERY_DATE);
        Delivery updated = deliveryWith(NEW_DELIVERY_DATE);
        Order order = orderWithAssemblyDate("order-1", ORIGINAL_DELIVERY_DATE, OrderStatus.New);
        when(deliveriesRepository.findById(STORE_ID, DELIVERY_ID)).thenReturn(existing);
        when(orderItemsRepository.findByDeliveryIdAndStatuses(eq(DELIVERY_ID), eq(Collections.singletonList(FulfilmentStatus.Ordered))))
                .thenReturn(List.of("order-1"));
        when(ordersRepository.findById(STORE_ID, "order-1")).thenReturn(order);

        // when
        deliveriesManager.updateDelivery(updated);

        // then
        verify(notificationEventPublisher, times(1))
                .publishAssemblyDateChanged(eq(order), eq(ORIGINAL_DELIVERY_DATE));
    }

    private Delivery deliveryWith(LocalDate estimatedDeliveryAt) {
        Delivery d = new Delivery();
        d.setStoreId(STORE_ID);
        d.setDeliveryId(DELIVERY_ID);
        d.setEstimatedDeliveryAt(estimatedDeliveryAt);
        return d;
    }

    private Order orderWithAssemblyDate(String orderId, LocalDate assemblyAt, OrderStatus status) {
        Order order = new Order(STORE_ID);
        order.setOrderId(orderId);
        order.setStatus(status);
        order.setOrderRealizationDays(2);
        order.updateEstimatedAssemblyAt(assemblyAt);
        return order;
    }
}
