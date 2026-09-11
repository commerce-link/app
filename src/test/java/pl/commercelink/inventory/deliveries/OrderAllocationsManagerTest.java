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
import pl.commercelink.orders.BillingDetails;
import pl.commercelink.orders.FulfilmentStatus;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.orders.OrderItemsRepository;
import pl.commercelink.orders.OrderStatus;
import pl.commercelink.orders.OrdersManager;
import pl.commercelink.orders.OrdersRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderAllocationsManagerTest {

    private static final String STORE_ID = "store-1";
    private static final String ORDER_ID = "order-1";

    @Mock
    private OrdersRepository ordersRepository;
    @Mock
    private OrderItemsRepository orderItemsRepository;
    @Mock
    private OrdersManager ordersManager;

    @InjectMocks
    private OrderAllocationsManager orderAllocationsManager;

    @Test
    @DisplayName("remove resets order status to New when at least one item fulfilment was removed")
    void removeResetsOrderStatusToNewWhenAtLeastOneFulfilmentRemoved() {
        // given
        Order order = orderWithStatus(OrderStatus.Assembly);
        OrderItem allocatedItem = orderItemInStatus("item-1", FulfilmentStatus.Allocation);
        when(orderItemsRepository.findById(ORDER_ID, "item-1")).thenReturn(allocatedItem);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);

        // when
        orderAllocationsManager.remove(STORE_ID, ORDER_ID, List.of("item-1"));

        // then
        verify(orderItemsRepository).save(allocatedItem);
        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(ordersRepository).save(orderCaptor.capture());
        assertThat(orderCaptor.getValue().getStatus()).isEqualTo(OrderStatus.New);
    }

    @Test
    @DisplayName("remove does not touch order when all items are outside Allocation/Ordered states")
    void removeDoesNotTouchOrderWhenAllItemsAreOutsideAllocationOrOrderedStates() {
        // given
        Order order = orderWithStatus(OrderStatus.Delivered);
        OrderItem deliveredItem = orderItemInStatus("item-1", FulfilmentStatus.Delivered);
        OrderItem returnedItem = orderItemInStatus("item-2", FulfilmentStatus.Returned);
        when(orderItemsRepository.findById(ORDER_ID, "item-1")).thenReturn(deliveredItem);
        when(orderItemsRepository.findById(ORDER_ID, "item-2")).thenReturn(returnedItem);

        // when
        orderAllocationsManager.remove(STORE_ID, ORDER_ID, List.of("item-1", "item-2"));

        // then
        verify(orderItemsRepository, never()).save(any());
        verify(ordersRepository, never()).save(any());
        verify(ordersRepository, never()).findById(any(), any());
    }

    @Test
    @DisplayName("remove leaves an allocation claimed by a pending delivery untouched")
    void removeLeavesAnAllocationClaimedByAPendingDeliveryUntouched() {
        // given
        OrderItem claimed = orderItemInStatus("item-1", FulfilmentStatus.Allocation);
        claimed.markAsClaimed("delivery-1");
        when(orderItemsRepository.findById(ORDER_ID, "item-1")).thenReturn(claimed);

        // when
        orderAllocationsManager.remove(STORE_ID, ORDER_ID, List.of("item-1"));

        // then
        assertThat(claimed.getClaimedDeliveryId()).isEqualTo("delivery-1");
        verify(orderItemsRepository, never()).save(claimed);
        verify(ordersRepository, never()).save(any());
    }

    @Test
    @DisplayName("remove only clears items that are in Allocation/Ordered state and ignores others")
    void removeOnlyClearsItemsThatAreInAllocationOrOrderedStateAndIgnoresRest() {
        // given
        Order order = orderWithStatus(OrderStatus.Assembly);
        OrderItem allocatedItem = orderItemInStatus("item-1", FulfilmentStatus.Allocation);
        OrderItem deliveredItem = orderItemInStatus("item-2", FulfilmentStatus.Delivered);
        when(orderItemsRepository.findById(ORDER_ID, "item-1")).thenReturn(allocatedItem);
        when(orderItemsRepository.findById(ORDER_ID, "item-2")).thenReturn(deliveredItem);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);

        // when
        orderAllocationsManager.remove(STORE_ID, ORDER_ID, List.of("item-1", "item-2"));

        // then
        verify(orderItemsRepository, times(1)).save(allocatedItem);
        verify(orderItemsRepository, never()).save(deliveredItem);
        verify(ordersRepository, times(1)).save(order);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.New);
    }

    @Test
    @DisplayName("reassign stamps the claimed delivery id of moved items with the target delivery")
    void reassignStampsClaimedDeliveryIdWithTargetDelivery() {
        // given
        OrderItem item = orderItemInStatus("item-1", FulfilmentStatus.Ordered);
        Allocation allocation = new Allocation();
        allocation.setKey(new AllocationKey(ORDER_ID, "item-1", "customer"));
        when(orderItemsRepository.findById(ORDER_ID, "item-1")).thenReturn(item);

        // when
        orderAllocationsManager.reassign("delivery-target", List.of(allocation));

        // then
        verify(orderItemsRepository).save(item);
        assertThat(item.getDeliveryId()).isEqualTo("delivery-target");
        assertThat(item.getClaimedDeliveryId()).isEqualTo("delivery-target");
    }

    @Test
    @DisplayName("release returns claimed order items to the supplier allocation pool, grouped by order")
    void releaseReturnsClaimedOrderItemsGroupedByOrder() {
        // given
        OrderItem first = orderItemInStatus("i1", FulfilmentStatus.Ordered);
        first.setOrderId("order-1");
        OrderItem second = orderItemInStatus("i2", FulfilmentStatus.Ordered);
        second.setOrderId("order-2");
        when(orderItemsRepository.findByDeliveryId("delivery-1")).thenReturn(List.of(first, second));

        // when
        orderAllocationsManager.release(STORE_ID, "delivery-1", "Acme");

        // then
        verify(ordersManager).returnOrderItemsToSupplierAllocation(STORE_ID, "order-1", "delivery-1", "Acme", List.of("i1"));
        verify(ordersManager).returnOrderItemsToSupplierAllocation(STORE_ID, "order-2", "delivery-1", "Acme", List.of("i2"));
    }

    @Test
    @DisplayName("updateFulfilment updates fulfilment data of item in Allocation state assigned to the given provider")
    void updateFulfilmentUpdatesItemInAllocationState() {
        // given
        OrderItem allocatedItem = orderItemInStatus("item-1", FulfilmentStatus.Allocation);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(orderWithStatus(OrderStatus.Assembly));
        when(orderItemsRepository.findById(ORDER_ID, "item-1")).thenReturn(allocatedItem);

        // when
        boolean updated = orderAllocationsManager.updateFulfilment(STORE_ID, "delivery-1", ORDER_ID, "item-1", "new-ean", "NEW-MFN", 55.5);

        // then
        assertThat(updated).isTrue();
        verify(orderItemsRepository).save(allocatedItem);
        assertThat(allocatedItem.getEan()).isEqualTo("new-ean");
        assertThat(allocatedItem.getManufacturerCode()).isEqualTo("NEW-MFN");
        assertThat(allocatedItem.getCost()).isEqualTo(55.5);
    }

    @Test
    @DisplayName("updateFulfilment does not touch item that is no longer in Allocation state")
    void updateFulfilmentSkipsItemOutsideAllocationState() {
        // given
        OrderItem orderedItem = orderItemInStatus("item-1", FulfilmentStatus.Ordered);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(orderWithStatus(OrderStatus.Assembly));
        when(orderItemsRepository.findById(ORDER_ID, "item-1")).thenReturn(orderedItem);

        // when
        boolean updated = orderAllocationsManager.updateFulfilment(STORE_ID, "delivery-1", ORDER_ID, "item-1", "new-ean", "NEW-MFN", 55.5);

        // then
        assertThat(updated).isFalse();
        verify(orderItemsRepository, never()).save(any());
        assertThat(orderedItem.getEan()).isEqualTo("EAN-item-1");
    }

    @Test
    @DisplayName("updateFulfilment does not touch item assigned to a different provider")
    void updateFulfilmentSkipsItemOfDifferentProvider() {
        // given
        OrderItem allocatedItem = orderItemInStatus("item-1", FulfilmentStatus.Allocation);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(orderWithStatus(OrderStatus.Assembly));
        when(orderItemsRepository.findById(ORDER_ID, "item-1")).thenReturn(allocatedItem);

        // when
        boolean updated = orderAllocationsManager.updateFulfilment(STORE_ID, "other-provider", ORDER_ID, "item-1", "new-ean", "NEW-MFN", 55.5);

        // then
        assertThat(updated).isFalse();
        verify(orderItemsRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateFulfilment does not touch item when order does not belong to the store")
    void updateFulfilmentSkipsItemWhenOrderNotFound() {
        // given
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(null);

        // when
        boolean updated = orderAllocationsManager.updateFulfilment(STORE_ID, "delivery-1", ORDER_ID, "item-1", "new-ean", "NEW-MFN", 55.5);

        // then
        assertThat(updated).isFalse();
        verify(orderItemsRepository, never()).save(any());
    }

    @Test
    @DisplayName("isClaimed reports true for an item claimed by a pending delivery")
    void isClaimedReportsTrueForAClaimedItem() {
        // given
        OrderItem claimed = orderItemInStatus("item-1", FulfilmentStatus.Allocation);
        claimed.markAsClaimed("delivery-1");
        when(orderItemsRepository.findById(ORDER_ID, "item-1")).thenReturn(claimed);

        // when / then
        assertThat(orderAllocationsManager.isClaimed(ORDER_ID, "item-1")).isTrue();
    }

    @Test
    @DisplayName("isClaimed reports false for an unclaimed item")
    void isClaimedReportsFalseForAnUnclaimedItem() {
        // given
        OrderItem free = orderItemInStatus("item-1", FulfilmentStatus.Allocation);
        when(orderItemsRepository.findById(ORDER_ID, "item-1")).thenReturn(free);

        // when / then
        assertThat(orderAllocationsManager.isClaimed(ORDER_ID, "item-1")).isFalse();
    }

    @Test
    @DisplayName("updateUnitCosts applies confirmed price and returns delta")
    void updateUnitCostsAppliesConfirmedPriceAndReturnsDelta() {
        // given
        OrderItem item = new OrderItem();
        item.setQty(2);
        item.setCost(100.0);
        item.setManufacturerCode("MFN-1");
        item.setStatus(FulfilmentStatus.Ordered);
        when(orderItemsRepository.findByDeliveryId("delivery-1")).thenReturn(List.of(item));

        // when
        double delta = orderAllocationsManager.updateUnitCosts("delivery-1", Map.of("MFN-1", 110.0));

        // then
        assertThat(delta).isEqualTo(20.0);
        assertThat(item.getCost()).isEqualTo(110.0);
        verify(orderItemsRepository).save(item);
    }

    @Test
    @DisplayName("updateUnitCosts skips returned items")
    void updateUnitCostsSkipsReturnedItems() {
        // given
        OrderItem item = new OrderItem();
        item.setQty(2);
        item.setCost(100.0);
        item.setManufacturerCode("MFN-1");
        item.setStatus(FulfilmentStatus.Returned);
        when(orderItemsRepository.findByDeliveryId("delivery-1")).thenReturn(List.of(item));

        // when
        double delta = orderAllocationsManager.updateUnitCosts("delivery-1", Map.of("MFN-1", 110.0));

        // then
        assertThat(delta).isZero();
        assertThat(item.getCost()).isEqualTo(100.0);
        verify(orderItemsRepository, never()).save(any(OrderItem.class));
    }

    @Test
    @DisplayName("updateUnitCosts updates replaced items without counting delta")
    void updateUnitCostsUpdatesReplacedItemsWithoutCountingDelta() {
        // given
        OrderItem item = new OrderItem();
        item.setQty(2);
        item.setCost(100.0);
        item.setManufacturerCode("MFN-1");
        item.setStatus(FulfilmentStatus.Replaced);
        when(orderItemsRepository.findByDeliveryId("delivery-1")).thenReturn(List.of(item));

        // when
        double delta = orderAllocationsManager.updateUnitCosts("delivery-1", Map.of("MFN-1", 110.0));

        // then
        assertThat(delta).isZero();
        assertThat(item.getCost()).isEqualTo(110.0);
        verify(orderItemsRepository).save(item);
    }

    @Test
    @DisplayName("updateUnitCosts ignores items without confirmed price")
    void updateUnitCostsIgnoresItemsWithoutConfirmedPrice() {
        // given
        OrderItem item = new OrderItem();
        item.setQty(2);
        item.setCost(100.0);
        item.setManufacturerCode("MFN-OTHER");
        item.setStatus(FulfilmentStatus.Ordered);
        when(orderItemsRepository.findByDeliveryId("delivery-1")).thenReturn(List.of(item));

        // when
        double delta = orderAllocationsManager.updateUnitCosts("delivery-1", Map.of("MFN-1", 110.0));

        // then
        assertThat(delta).isZero();
        verify(orderItemsRepository, never()).save(any(OrderItem.class));
    }

    @Test
    @DisplayName("propagateEstimatedDeliveryAt updates every order with items ordered in the delivery")
    void propagateEstimatedDeliveryAtUpdatesEveryOrderWithItemsOrderedInTheDelivery() {
        // given
        LocalDate estimatedDeliveryAt = LocalDate.of(2026, 9, 15);
        when(orderItemsRepository.findByDeliveryIdAndStatuses("delivery-1", List.of(FulfilmentStatus.Ordered)))
                .thenReturn(List.of("order-1", "order-2"));

        // when
        orderAllocationsManager.propagateEstimatedDeliveryAt(STORE_ID, "delivery-1", estimatedDeliveryAt);

        // then
        verify(ordersManager, times(1)).updateEstimatedDeliveryAt(STORE_ID, "order-1", estimatedDeliveryAt);
        verify(ordersManager, times(1)).updateEstimatedDeliveryAt(STORE_ID, "order-2", estimatedDeliveryAt);
    }

    @Test
    @DisplayName("propagateEstimatedDeliveryAt continues with the other orders when one fails")
    void propagateEstimatedDeliveryAtContinuesWithTheOtherOrdersWhenOneFails() {
        // given
        LocalDate estimatedDeliveryAt = LocalDate.of(2026, 9, 15);
        when(orderItemsRepository.findByDeliveryIdAndStatuses("delivery-1", List.of(FulfilmentStatus.Ordered)))
                .thenReturn(List.of("order-1", "order-2"));
        doThrow(new IllegalStateException("version conflict"))
                .when(ordersManager).updateEstimatedDeliveryAt(STORE_ID, "order-1", estimatedDeliveryAt);

        // when
        orderAllocationsManager.propagateEstimatedDeliveryAt(STORE_ID, "delivery-1", estimatedDeliveryAt);

        // then
        verify(ordersManager).updateEstimatedDeliveryAt(STORE_ID, "order-2", estimatedDeliveryAt);
    }

    @Test
    @DisplayName("propagateEstimatedDeliveryAt is a no-op without a date")
    void propagateEstimatedDeliveryAtIsANoOpWithoutADate() {
        // when
        orderAllocationsManager.propagateEstimatedDeliveryAt(STORE_ID, "delivery-1", null);

        // then
        verify(orderItemsRepository, never()).findByDeliveryIdAndStatuses(any(), any());
        verify(ordersManager, never()).updateEstimatedDeliveryAt(any(), any(), any());
    }

    @Test
    @DisplayName("claim hands the selected order allocations to the orders manager without ordering them")
    void claimHandsTheSelectedOrderAllocationsToTheOrdersManagerWithoutOrderingThem() {
        // given
        DeliveryItem item = deliveryItemWithSelectedOrderAllocation("item-1", 42.0);

        // when
        orderAllocationsManager.claim(STORE_ID, "delivery-1", List.of(item));

        // then
        verify(ordersManager).claimOrderItems(STORE_ID, ORDER_ID, "delivery-1", Map.of("item-1", 42.0));
        verify(ordersManager, never()).markOrderItemsAsOrdered(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("markClaimedAsOrdered orders every claimed item of the delivery with the confirmed date")
    void markClaimedAsOrderedOrdersEveryClaimedItemOfTheDeliveryWithTheConfirmedDate() {
        // given
        LocalDate confirmed = LocalDate.of(2026, 9, 25);
        OrderItem claimed = orderItemInStatus("item-1", FulfilmentStatus.Allocation);
        claimed.setCost(42.0);
        claimed.markAsClaimed("delivery-1");
        when(orderItemsRepository.findByDeliveryId("delivery-1")).thenReturn(List.of(claimed));
        when(orderItemsRepository.findByDeliveryIdAndStatuses("delivery-1", List.of(FulfilmentStatus.Ordered)))
                .thenReturn(List.of());

        // when
        orderAllocationsManager.markClaimedAsOrdered(STORE_ID, "delivery-1", confirmed);

        // then
        verify(ordersManager).markOrderItemsAsOrdered(STORE_ID, ORDER_ID, "delivery-1", Map.of("item-1", 42.0), confirmed);
    }

    @Test
    @DisplayName("markClaimedAsOrdered continues with the other orders when one fails")
    void markClaimedAsOrderedContinuesWithTheOtherOrdersWhenOneFails() {
        // given
        LocalDate confirmed = LocalDate.of(2026, 9, 25);
        OrderItem first = orderItemInStatus("item-1", FulfilmentStatus.Allocation);
        first.markAsClaimed("delivery-1");
        OrderItem second = orderItemInStatus("item-2", FulfilmentStatus.Allocation);
        second.setOrderId("order-2");
        second.markAsClaimed("delivery-1");
        when(orderItemsRepository.findByDeliveryId("delivery-1")).thenReturn(List.of(first, second));
        when(orderItemsRepository.findByDeliveryIdAndStatuses("delivery-1", List.of(FulfilmentStatus.Ordered)))
                .thenReturn(List.of());
        doThrow(new RuntimeException("boom")).when(ordersManager)
                .markOrderItemsAsOrdered(eq(STORE_ID), eq(ORDER_ID), any(), any(), any());

        // when
        orderAllocationsManager.markClaimedAsOrdered(STORE_ID, "delivery-1", confirmed);

        // then
        verify(ordersManager).markOrderItemsAsOrdered(eq(STORE_ID), eq("order-2"), eq("delivery-1"), any(), eq(confirmed));
    }

    @Test
    @DisplayName("markClaimedAsOrdered only applies the date to orders whose items were already ordered before the deploy")
    void markClaimedAsOrderedOnlyAppliesTheDateToOrdersWhoseItemsWereAlreadyOrderedBeforeTheDeploy() {
        // given
        LocalDate confirmed = LocalDate.of(2026, 9, 25);
        when(orderItemsRepository.findByDeliveryId("delivery-1")).thenReturn(List.of());
        when(orderItemsRepository.findByDeliveryIdAndStatuses("delivery-1", List.of(FulfilmentStatus.Ordered)))
                .thenReturn(List.of(ORDER_ID));

        // when
        orderAllocationsManager.markClaimedAsOrdered(STORE_ID, "delivery-1", confirmed);

        // then
        verify(ordersManager).updateEstimatedDeliveryAt(STORE_ID, ORDER_ID, confirmed);
        verify(ordersManager, never()).markOrderItemsAsOrdered(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("markClaimedAsOrdered is a no-op without a date")
    void markClaimedAsOrderedIsANoOpWithoutADate() {
        // when
        orderAllocationsManager.markClaimedAsOrdered(STORE_ID, "delivery-1", null);

        // then
        verify(ordersManager, never()).markOrderItemsAsOrdered(any(), any(), any(), any(), any());
        verify(ordersManager, never()).updateEstimatedDeliveryAt(any(), any(), any());
    }

    @Test
    @DisplayName("fetchAll skips allocations already claimed by a delivery")
    void fetchAllSkipsAllocationsAlreadyClaimedByADelivery() {
        // given
        Order order = orderWithStatus(OrderStatus.New);
        OrderItem free = orderItemInStatus("item-1", FulfilmentStatus.Allocation);
        OrderItem claimed = orderItemInStatus("item-2", FulfilmentStatus.Allocation);
        claimed.markAsClaimed("delivery-1");
        when(ordersRepository.findAllByStoreIdAndStatus(STORE_ID, OrderStatus.New, OrderStatus.Assembly))
                .thenReturn(List.of(order));
        when(orderItemsRepository.findByOrderIdAndStatus(ORDER_ID, FulfilmentStatus.Allocation))
                .thenReturn(List.of(free, claimed));

        // when
        List<Allocation> allocations = orderAllocationsManager.fetchAll(STORE_ID);

        // then
        assertThat(allocations).hasSize(1);
        assertThat(allocations.get(0).getKey().getItemId()).isEqualTo("item-1");
    }

    private DeliveryItem deliveryItemWithSelectedOrderAllocation(String itemId, double unitCost) {
        Allocation allocation = new Allocation();
        allocation.setKey(new AllocationKey(ORDER_ID, itemId, "buyer@example.com"));
        allocation.setType(AllocationType.Order);
        allocation.setQty(1);
        allocation.setSelected(true);

        DeliveryItem deliveryItem = new DeliveryItem();
        deliveryItem.setMfn("MFN-" + itemId);
        deliveryItem.setUnitCost(unitCost);
        deliveryItem.setRequestedQty(1);
        deliveryItem.setAllocations(List.of(allocation));
        return deliveryItem;
    }

    private Order orderWithStatus(OrderStatus status) {
        Order order = new Order(STORE_ID);
        order.setOrderId(ORDER_ID);
        order.setStatus(status);
        // set up minimal billing details for Allocation.fromOrderItem
        BillingDetails billingDetails = new BillingDetails();
        billingDetails.setEmail("buyer@example.com");
        order.setBillingDetails(billingDetails);
        return order;
    }

    private OrderItem orderItemInStatus(String itemId, FulfilmentStatus status) {
        OrderItem item = new OrderItem(ORDER_ID, "Other", "test", 1, 100.0, "SKU-" + itemId, false);
        item.setItemId(itemId);
        item.setStatus(status);
        if (status == FulfilmentStatus.Allocation || status == FulfilmentStatus.Ordered) {
            item.setEan("EAN-" + itemId);
            item.setManufacturerCode("MFN-" + itemId);
            item.setDeliveryId("delivery-1");
        }
        return item;
    }
}
