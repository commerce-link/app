package pl.commercelink.inventory.deliveries;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pl.commercelink.orders.FulfilmentStatus;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.orders.OrderItemsRepository;
import pl.commercelink.orders.OrderStatus;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.orders.notifications.OrderNotificationsEventPublisher;
import pl.commercelink.stores.ConnectionMode;
import pl.commercelink.warehouse.builtin.WarehouseAllocationsManager;

import java.time.LocalDate;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
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
    private DropshipItemLookup dropshipItemLookup;

    @InjectMocks
    private DeliveriesManager deliveriesManager;

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

    @Test
    @DisplayName("pushing a date on an all-dropship order keeps assembly and shipping on the same day")
    void updateDeliveryKeepsDropshipDatesTogetherWhenDelayed() {
        // given
        Delivery existing = deliveryWith(ORIGINAL_DELIVERY_DATE);
        existing.setType(DeliveryType.DROPSHIP);
        Delivery updated = deliveryWith(NEW_DELIVERY_DATE);
        updated.setType(DeliveryType.DROPSHIP);
        Order order = orderWithAssemblyDate("order-1", ORIGINAL_DELIVERY_DATE, OrderStatus.Assembly);
        order.setOrderRealizationDays(3);
        when(deliveriesRepository.findById(STORE_ID, DELIVERY_ID)).thenReturn(existing);
        when(orderItemsRepository.findByDeliveryIdAndStatuses(eq(DELIVERY_ID),
                eq(Collections.singletonList(FulfilmentStatus.Ordered)))).thenReturn(List.of("order-1"));
        when(ordersRepository.findById(STORE_ID, "order-1")).thenReturn(order);
        when(dropshipItemLookup.isEntirelyDropship(eq(STORE_ID), any())).thenReturn(true);

        // when
        deliveriesManager.updateDelivery(updated);

        // then
        assertThat(order.getEstimatedAssemblyAt()).isEqualTo(NEW_DELIVERY_DATE);
        assertThat(order.getEstimatedShippingAt()).isEqualTo(NEW_DELIVERY_DATE);
    }

    @Test
    @DisplayName("pushing a date on an order with a warehouse leg leaves shipping trailing assembly")
    void updateDeliveryKeepsWarehouseShippingLaterThanAssemblyWhenDelayed() {
        // given
        Delivery existing = deliveryWith(ORIGINAL_DELIVERY_DATE);
        existing.setType(DeliveryType.WAREHOUSE);
        Delivery updated = deliveryWith(NEW_DELIVERY_DATE);
        updated.setType(DeliveryType.WAREHOUSE);
        Order order = orderWithAssemblyDate("order-1", ORIGINAL_DELIVERY_DATE, OrderStatus.Assembly);
        order.setOrderRealizationDays(3);
        when(deliveriesRepository.findById(STORE_ID, DELIVERY_ID)).thenReturn(existing);
        when(orderItemsRepository.findByDeliveryIdAndStatuses(eq(DELIVERY_ID),
                eq(Collections.singletonList(FulfilmentStatus.Ordered)))).thenReturn(List.of("order-1"));
        when(ordersRepository.findById(STORE_ID, "order-1")).thenReturn(order);
        when(dropshipItemLookup.isEntirelyDropship(eq(STORE_ID), any())).thenReturn(false);

        // when
        deliveriesManager.updateDelivery(updated);

        // then
        assertThat(order.getEstimatedAssemblyAt()).isEqualTo(NEW_DELIVERY_DATE);
        assertThat(order.getEstimatedShippingAt()).isEqualTo(NEW_DELIVERY_DATE.plusDays(3));
    }

    @Test
    @DisplayName("a delayed dropship delivery on a mixed order still leaves the realization days in place")
    void updateDeliveryKeepsRealizationDaysWhenTheOrderAlsoTravelsThroughTheWarehouse() {
        // given: the delivery whose date moved is a dropship one, but the order it feeds also has a leg
        // through our warehouse. Asking the delivery would answer "dropship" and drop the handling time;
        // only asking the order gets it right.
        Delivery existing = deliveryWith(ORIGINAL_DELIVERY_DATE);
        existing.setType(DeliveryType.DROPSHIP);
        Delivery updated = deliveryWith(NEW_DELIVERY_DATE);
        updated.setType(DeliveryType.DROPSHIP);
        Order order = orderWithAssemblyDate("order-1", ORIGINAL_DELIVERY_DATE, OrderStatus.Assembly);
        order.setOrderRealizationDays(3);
        OrderItem dropshipItem = orderItem("item-1", DELIVERY_ID);
        OrderItem warehouseItem = orderItem("item-2", "warehouse-delivery");
        when(deliveriesRepository.findById(STORE_ID, DELIVERY_ID)).thenReturn(existing);
        when(orderItemsRepository.findByDeliveryIdAndStatuses(eq(DELIVERY_ID),
                eq(Collections.singletonList(FulfilmentStatus.Ordered)))).thenReturn(List.of("order-1"));
        when(ordersRepository.findById(STORE_ID, "order-1")).thenReturn(order);
        when(orderItemsRepository.findByOrderId("order-1")).thenReturn(List.of(dropshipItem, warehouseItem));
        when(dropshipItemLookup.isEntirelyDropship(eq(STORE_ID), any())).thenReturn(false);

        // when
        deliveriesManager.updateDelivery(updated);

        // then: 2026-05-10 is a Sunday, so +3 working days lands on Wednesday 2026-05-13
        assertThat(order.getEstimatedAssemblyAt()).isEqualTo(NEW_DELIVERY_DATE);
        assertThat(order.getEstimatedShippingAt()).isEqualTo(LocalDate.of(2026, 5, 13));
        ArgumentCaptor<List<OrderItem>> askedWith = ArgumentCaptor.forClass(List.class);
        verify(dropshipItemLookup).isEntirelyDropship(eq(STORE_ID), askedWith.capture());
        assertThat(askedWith.getValue()).containsExactly(dropshipItem, warehouseItem);
    }

    private static OrderItem orderItem(String itemId, String deliveryId) {
        OrderItem item = new OrderItem("order-1", "Category", "Product " + itemId, 1, 100.0, "MFN-" + itemId, false);
        item.setItemId(itemId);
        item.setDeliveryId(deliveryId);
        return item;
    }

    @Test
    void updateDeliverySetsEstimatedDateWhenExistingDeliveryHasNone() {
        // given
        Delivery existing = deliveryWith(null);
        Delivery updated = deliveryWith(NEW_DELIVERY_DATE);
        when(deliveriesRepository.findById(STORE_ID, DELIVERY_ID)).thenReturn(existing);

        // when
        deliveriesManager.updateDelivery(updated);

        // then
        assertThat(existing.getEstimatedDeliveryAt()).isEqualTo(NEW_DELIVERY_DATE);
        verify(deliveriesRepository).save(existing);
        verify(notificationEventPublisher, never()).publishAssemblyDateChanged(any(), any());
    }

    @Test
    void splitTargetInheritsTheConnectionModeOfTheSourceDelivery() {
        // given
        Delivery source = deliveryWith(ORIGINAL_DELIVERY_DATE);
        source.setProvider("Elko");
        source.setConnectionMode(ConnectionMode.GLOBAL);
        when(deliveriesRepository.findById(STORE_ID, DELIVERY_ID)).thenReturn(source);
        Allocation warehouseAllocation = new Allocation();
        warehouseAllocation.setQty(1);
        warehouseAllocation.setUnitCost(50.0);

        // when
        deliveriesManager.splitAllocations(STORE_ID, DELIVERY_ID, "EXT-1", NEW_DELIVERY_DATE,
                List.of(), List.of(warehouseAllocation));

        // then
        ArgumentCaptor<Delivery> saved = ArgumentCaptor.forClass(Delivery.class);
        verify(deliveriesRepository, times(2)).save(saved.capture());
        Delivery target = saved.getAllValues().get(1);
        assertThat(target.getConnectionMode()).isEqualTo(ConnectionMode.GLOBAL);
    }

    @Test
    void splitOfAnAwaitingApprovalDeliveryCreatesAnAwaitingApprovalTarget() {
        // given
        Delivery source = deliveryWith(ORIGINAL_DELIVERY_DATE);
        source.setProvider("Elko");
        source.setConnectionMode(ConnectionMode.GLOBAL);
        source.setOrderStatus(DeliveryOrderStatus.AWAITING_APPROVAL);
        source.setPurchaseRef("source-ref");
        source.setDeliveryAddressId("addr-1");
        when(deliveriesRepository.findById(STORE_ID, DELIVERY_ID)).thenReturn(source);
        Allocation warehouseAllocation = new Allocation();
        warehouseAllocation.setQty(1);
        warehouseAllocation.setUnitCost(50.0);

        // when
        deliveriesManager.splitAllocations(STORE_ID, DELIVERY_ID, "EXT-1", NEW_DELIVERY_DATE,
                List.of(), List.of(warehouseAllocation));

        // then
        ArgumentCaptor<Delivery> saved = ArgumentCaptor.forClass(Delivery.class);
        verify(deliveriesRepository, times(2)).save(saved.capture());
        Delivery target = saved.getAllValues().get(1);
        assertThat(target.isAwaitingApproval()).isTrue();
        assertThat(target.getPurchaseRef()).isNotBlank().isNotEqualTo("source-ref");
        assertThat(target.getDeliveryAddressId()).isEqualTo("addr-1");
    }

    @Test
    void splitOfAFailedDeliveryCreatesAFailedTargetWithAFreshPurchaseRef() {
        // given
        Delivery source = deliveryWith(ORIGINAL_DELIVERY_DATE);
        source.setProvider("Elko");
        source.setConnectionMode(ConnectionMode.GLOBAL);
        source.setOrderStatus(DeliveryOrderStatus.FAILED);
        source.setPurchaseRef("source-ref");
        source.setDeliveryAddressId("addr-1");
        source.setOrderErrorMessage("Out of stock");
        source.setSupplierOrderChoices(new HashMap<>(Map.of("paymentMethod", "1.Przelew")));
        source.setSupplierOrderChoicesLabel("Sposób zapłaty: 1.Przelew");
        when(deliveriesRepository.findById(STORE_ID, DELIVERY_ID)).thenReturn(source);
        Allocation warehouseAllocation = new Allocation();
        warehouseAllocation.setQty(1);
        warehouseAllocation.setUnitCost(50.0);

        // when
        deliveriesManager.splitAllocations(STORE_ID, DELIVERY_ID, "EXT-1", NEW_DELIVERY_DATE,
                List.of(), List.of(warehouseAllocation));

        // then
        ArgumentCaptor<Delivery> saved = ArgumentCaptor.forClass(Delivery.class);
        verify(deliveriesRepository, times(2)).save(saved.capture());
        Delivery target = saved.getAllValues().get(1);
        assertThat(target.getOrderStatus()).isEqualTo(DeliveryOrderStatus.FAILED);
        assertThat(target.getPurchaseRef()).isNotBlank().isNotEqualTo("source-ref");
        assertThat(target.getDeliveryAddressId()).isEqualTo("addr-1");
        assertThat(target.getOrderErrorMessage()).isEqualTo("Out of stock");
        assertThat(target.getSupplierOrderChoices()).containsExactlyEntriesOf(Map.of("paymentMethod", "1.Przelew"));
        assertThat(target.getSupplierOrderChoicesLabel()).isEqualTo("Sposób zapłaty: 1.Przelew");
    }

    @Test
    void splitOfADispatchedDeliveryCreatesADispatchedTargetWithAFreshPurchaseRef() {
        // given
        Delivery source = deliveryWith(ORIGINAL_DELIVERY_DATE);
        source.setProvider("Elko");
        source.setConnectionMode(ConnectionMode.GLOBAL);
        source.setOrderStatus(DeliveryOrderStatus.ORDER_DISPATCHED);
        source.setPurchaseRef("source-ref");
        source.setDeliveryAddressId("addr-1");
        when(deliveriesRepository.findById(STORE_ID, DELIVERY_ID)).thenReturn(source);
        Allocation warehouseAllocation = new Allocation();
        warehouseAllocation.setQty(1);
        warehouseAllocation.setUnitCost(50.0);

        // when
        deliveriesManager.splitAllocations(STORE_ID, DELIVERY_ID, "EXT-1", NEW_DELIVERY_DATE,
                List.of(), List.of(warehouseAllocation));

        // then
        ArgumentCaptor<Delivery> saved = ArgumentCaptor.forClass(Delivery.class);
        verify(deliveriesRepository, times(2)).save(saved.capture());
        Delivery target = saved.getAllValues().get(1);
        assertThat(target.getOrderStatus()).isEqualTo(DeliveryOrderStatus.ORDER_DISPATCHED);
        assertThat(target.getPurchaseRef()).isNotBlank().isNotEqualTo("source-ref");
        assertThat(target.getDeliveryAddressId()).isEqualTo("addr-1");
    }

    @Test
    void splitOfARegularDeliveryLeavesTheTargetWithoutApprovalState() {
        // given
        Delivery source = deliveryWith(ORIGINAL_DELIVERY_DATE);
        source.setProvider("Elko");
        source.setConnectionMode(ConnectionMode.OWN);
        when(deliveriesRepository.findById(STORE_ID, DELIVERY_ID)).thenReturn(source);
        Allocation warehouseAllocation = new Allocation();
        warehouseAllocation.setQty(1);
        warehouseAllocation.setUnitCost(50.0);

        // when
        deliveriesManager.splitAllocations(STORE_ID, DELIVERY_ID, "EXT-1", NEW_DELIVERY_DATE,
                List.of(), List.of(warehouseAllocation));

        // then
        ArgumentCaptor<Delivery> saved = ArgumentCaptor.forClass(Delivery.class);
        verify(deliveriesRepository, times(2)).save(saved.capture());
        Delivery target = saved.getAllValues().get(1);
        assertThat(target.getOrderStatus()).isNull();
        assertThat(target.getPurchaseRef()).isNull();
    }

    @Test
    @DisplayName("deleteAllocations on a failed delivery frees its claimed items and decreases the cost by what was removed")
    void deleteAllocationsOnAFailedDeliveryFreesClaimedItemsAndDecreasesCostByWhatWasRemoved() {
        // given
        Delivery delivery = deliveryWith(ORIGINAL_DELIVERY_DATE);
        delivery.setOrderStatus(DeliveryOrderStatus.FAILED);
        delivery.setTotalCost(300.0);
        Allocation claimedByThisDelivery = orderAllocation("order-1", "item-1", 2, 50.0);
        Allocation claimedByAnother = orderAllocation("order-2", "item-2", 1, 100.0);
        when(deliveriesRepository.findById(STORE_ID, DELIVERY_ID)).thenReturn(delivery);
        when(orderAllocationsManager.remove(STORE_ID, "order-1", "item-1", DELIVERY_ID)).thenReturn(true);
        when(orderAllocationsManager.remove(STORE_ID, "order-2", "item-2", DELIVERY_ID)).thenReturn(false);

        // when
        deliveriesManager.deleteAllocations(STORE_ID, DELIVERY_ID, List.of(claimedByThisDelivery, claimedByAnother));

        // then
        verify(orderAllocationsManager).remove(STORE_ID, "order-1", "item-1", DELIVERY_ID);
        verify(orderAllocationsManager).remove(STORE_ID, "order-2", "item-2", DELIVERY_ID);
        assertThat(delivery.getTotalCost()).isEqualTo(200.0);
        verify(deliveriesRepository).save(delivery);
    }

    private Allocation orderAllocation(String orderId, String itemId, int qty, double unitCost) {
        Allocation allocation = new Allocation();
        allocation.setKey(new AllocationKey(orderId, itemId, "buyer@example.com"));
        allocation.setType(AllocationType.Order);
        allocation.setQty(qty);
        allocation.setUnitCost(unitCost);
        allocation.setInAllocation(true);
        return allocation;
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
        order.updateEstimatedAssemblyAt(assemblyAt, false);
        return order;
    }
}
