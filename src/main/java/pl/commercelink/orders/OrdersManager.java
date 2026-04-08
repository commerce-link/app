package pl.commercelink.orders;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import pl.commercelink.inventory.MatchedInventory;
import pl.commercelink.orders.fulfilment.OrderFulfilmentEventPublisher;
import pl.commercelink.pricelist.AvailabilityAndPrice;
import pl.commercelink.taxonomy.ProductCategory;
import pl.commercelink.stores.Store;
import pl.commercelink.inventory.supplier.api.Taxonomy;
import pl.commercelink.warehouse.api.Reservation;
import pl.commercelink.warehouse.api.ReservationRemovalItem;
import pl.commercelink.warehouse.api.Warehouse;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Component
public class OrdersManager {

    @Autowired
    private Warehouse warehouse;
    @Autowired
    private OrdersRepository ordersRepository;
    @Autowired
    private OrderItemsRepository orderItemsRepository;
    @Autowired
    private OrderFulfilmentEventPublisher orderFulfilmentEventPublisher;
    @Autowired
    private OrderLifecycle orderLifecycle;

    public void addOrderItem(Store store, Order order, MatchedInventory matchedInventory) {
        OrderItem orderItem;
        if (!matchedInventory.hasAnyOffers()) {
            String mfn = matchedInventory.getInventoryKey().getProductCodes().iterator().next();
            orderItem = new OrderItem(order.getOrderId(), ProductCategory.Other, "", 1, 0, mfn, store.isPositionConsolidationEnabled());
        } else {
            Taxonomy taxonomy = matchedInventory.getTaxonomy();
            orderItem = new OrderItem(
                    order.getOrderId(),
                    taxonomy.category(),
                    taxonomy.name(),
                    1,
                    matchedInventory.getMedianPrice().grossValue(),
                    taxonomy.mfn(),
                    store.isPositionConsolidationEnabled()
            );
        }
        orderItemsRepository.save(orderItem);

        order.increaseRealizationDays(orderItem, matchedInventory.getEstimatedDeliveryDays());
        order.increaseTotalPrice(orderItem.getTotalPrice());
        ordersRepository.save(order);
    }

    public void addOrderItem(Store store, Order order, AvailabilityAndPrice availabilityAndPrice) {
        OrderItem orderItem = new OrderItem(
                order.getOrderId(),
                availabilityAndPrice.getCategory(),
                availabilityAndPrice.getName(),
                1,
                availabilityAndPrice.getPrice(),
                availabilityAndPrice.getManufacturerCode(),
                store.isPositionConsolidationEnabled()
        );
        if (orderItem.hasCategory(ProductCategory.Services)) {
            orderItem.markAsWarehouseFulfilled();
        }

        orderItemsRepository.save(orderItem);

        order.increaseRealizationDays(orderItem, availabilityAndPrice.getEstimatedDeliveryDays());
        order.increaseTotalPrice(orderItem.getTotalPrice());
        ordersRepository.save(order);
    }

    public Result removeFromOrder(String storeId, String orderId, List<String> orderItemIds) {
        Order order = ordersRepository.findById(storeId, orderId);
        List<OrderItem> orderItems = orderItemsRepository.findByOrderId(order.getOrderId());
        List<OrderItem> selectedOrderItems = orderItems.stream()
                .filter(i -> orderItemIds.contains(i.getItemId()))
                .collect(Collectors.toList());

        for (OrderItem selectedOrderItem : selectedOrderItems) {
            if (selectedOrderItem.isNew() || selectedOrderItem.getCategory() == ProductCategory.Services) {
                orderItems.remove(selectedOrderItem);
                orderItemsRepository.delete(selectedOrderItem);

                order.decreaseTotalPrice(selectedOrderItem.getTotalPrice());
            }
        }

        orderLifecycle.update(order);
        return new Result(order, orderItems);
    }

    public void markOrderItemsAsOrdered(String storeId, String orderId, String deliveryId, Map<String, Double> orderItemId2Costs, LocalDate estimatedDeliveryAt) {
        execute(storeId, orderId, orderItemId2Costs.keySet(), (order, orderItem) -> {
            if (orderItem.isInAllocation()) {
                orderItem.markAsOrdered(deliveryId, orderItemId2Costs.get(orderItem.getItemId()));
                orderItemsRepository.save(orderItem);
            }
        }, o -> o.updateEstimatedAssemblyAt(estimatedDeliveryAt));
    }

    public Result moveItemsToAllocation(String storeId, String orderId, List<String> orderItemIds) {
        return execute(storeId, orderId, orderItemIds, (order, orderItem) -> {
            if (orderItem.isReadyForAllocation()) {
                orderItem.markAsInAllocation();
                orderItemsRepository.save(orderItem);
            }
        });
    }

    public Result moveOrderItemsToTheWarehouseForRMA(String storeId, String orderId, List<String> orderItemIds) {
        return execute(storeId, orderId, orderItemIds, (order, orderItem) -> {
            if (orderItem.isDelivered()) {
                warehouse.reservationService(storeId)
                        .remove(
                                Reservation.orderFulfilmentToRMA(
                                        storeId,
                                        ReservationRemovalItem.from(orderItem)
                                )
                        );

                orderItem.removeFulfilment();
                orderItemsRepository.save(orderItem);
            }
        });
    }

    public Result moveOrderItemsToTheWarehouse(String storeId, String orderId, List<String> orderItemIds) {
        return execute(storeId, orderId, orderItemIds, (order, orderItem) -> {
            if (orderItem.isAllocated()) {
                warehouse.reservationService(storeId)
                        .remove(
                                Reservation.orderFulfilmentToStock(
                                        storeId,
                                        ReservationRemovalItem.from(orderItem)
                                )
                        );

                orderItem.removeFulfilment();
                orderItemsRepository.save(orderItem);
            }
        });
    }

    private Result execute(String storeId, String orderId, Collection<String> orderItemIds, BiConsumer<Order, OrderItem> action) {
        return execute(storeId, orderId, orderItemIds, action, o -> { });
    }

    private Result execute(String storeId, String orderId, Collection<String> orderItemIds, BiConsumer<Order, OrderItem> action, Consumer<Order> lifecycleAction) {
        Order order = ordersRepository.findById(storeId, orderId);
        List<OrderItem> orderItems = orderItemsRepository.findByOrderId(order.getOrderId());

        orderItems.stream().filter(i -> orderItemIds.contains(i.getItemId())).forEach(i -> {
            action.accept(order, i);
        });

        lifecycleAction.accept(order);
        orderLifecycle.update(order, orderItems);

        return new Result(order, orderItems);
    }

    public void deleteOrder(String storeId, String orderId) {
        Order order = ordersRepository.findById(storeId, orderId);
        List<OrderItem> orderItems = orderItemsRepository.findByOrderId(orderId);

        if (!order.hasStatus(OrderStatus.New)) {
            throw new IllegalStateException("Order must be in New status to be deleted");
        }
        if (!orderItems.isEmpty()) {
            throw new IllegalStateException("Order must have no items to be deleted");
        }
        if (order.isInvoiced()) {
            throw new IllegalStateException("Order must not have an invoice to be deleted");
        }

        ordersRepository.delete(order);
    }

    public void saveWithFulfilment(Order order, List<OrderItem> orderItems) {
        // prevent duplicate orders based on externalOrderId
        if (StringUtils.isNotBlank(order.getExternalOrderId())) {
            Order existingOrder = ordersRepository.findByStoreIdAndExternalOrderId(order.getStoreId(), order.getExternalOrderId());
            if (existingOrder != null) {
                return;
            }
        }

        ordersRepository.save(order);
        orderItemsRepository.batchSave(orderItems);
        orderLifecycle.update(order);

        orderFulfilmentEventPublisher.publish(order.getStoreId(), order.getOrderId());
    }

    public static class Result {
        private final Order order;
        private final List<OrderItem> orderItems;

        public Result(Order order, List<OrderItem> orderItems) {
            this.order = order;
            this.orderItems = orderItems;
        }

        public Order getOrder() {
            return order;
        }

        public List<OrderItem> getOrderItems() {
            return orderItems;
        }
    }
}
