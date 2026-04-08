package pl.commercelink.orders.fulfilment;

import pl.commercelink.orders.*;
import pl.commercelink.taxonomy.ProductCategory;
import pl.commercelink.warehouse.WarehouseFulfilmentService;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

abstract class OrderFulfilment {

    final OrderItemsRepository orderItemsRepository;

    private final OrdersRepository ordersRepository;
    private final OrderLifecycle orderLifecycle;
    private final WarehouseFulfilmentService warehouseFulfilmentService;

    OrderFulfilment(OrdersRepository ordersRepository, OrderItemsRepository orderItemsRepository, OrderLifecycle orderLifecycle, WarehouseFulfilmentService warehouseFulfilmentService) {
        this.orderLifecycle = orderLifecycle;
        this.orderItemsRepository = orderItemsRepository;
        this.ordersRepository = ordersRepository;
        this.warehouseFulfilmentService = warehouseFulfilmentService;
    }

    /*
        * Returns only accepted order items.
     */
    Optional<OrderItem> accept(OrderItem orderItem, List<FulfilmentItem> candidates) {
        Optional<FulfilmentItem> firstCandidate = candidates.stream()
                .filter(FulfilmentItem::isAccepted)
                .filter(c -> c.isFor(orderItem))
                .filter(FulfilmentItem::hasProvider)
                .findFirst();

        if (firstCandidate.isPresent()) {
            orderItem.addFulfilment(firstCandidate.get().getSource());
            return Optional.of(orderItem);
        }

        return Optional.empty();
    }

    void commit(String storeId, List<OrderItem> acceptedOrderItems) {
        Map<String, List<OrderItem>> groupedByOrderId = acceptedOrderItems.stream()
                .collect(Collectors.groupingBy(OrderItem::getOrderId));

        for (Map.Entry<String, List<OrderItem>> entry : groupedByOrderId.entrySet()) {
            commit(storeId, entry.getKey(), entry.getValue());
        }
    }

    private void commit(String storeId, String orderId, List<OrderItem> orderItems) {
        Order order = ordersRepository.findById(storeId, orderId);

        List<OrderItem> acceptedProducts = orderItems.stream()
                .filter(i -> !i.isInAllocation())
                .filter(i -> !i.isAllocated())
                .filter(i -> !i.hasCategory(ProductCategory.Services))
                .collect(Collectors.toList());

        List<OrderItem> acceptedServices = orderItems.stream()
                .filter(i -> !i.isInAllocation())
                .filter(i -> !i.isAllocated())
                .filter(i -> i.hasCategory(ProductCategory.Services))
                .peek(OrderItem::markAsReceived)
                .collect(Collectors.toList());

        List<OrderItem> fulfilledProducts = new LinkedList<>();
        fulfilledProducts.addAll(runServicesFulfilment(acceptedServices));
        fulfilledProducts.addAll(runWarehouseFulfilment(order, acceptedProducts));
        fulfilledProducts.addAll(runProvidersFulfilment(acceptedProducts));

        ordersRepository.save(order);
        orderItemsRepository.batchSave(fulfilledProducts);

        if (fulfilledProducts.stream().allMatch(Item::isDelivered)) {
            orderLifecycle.update(order);
        }
    }

    private List<OrderItem> runServicesFulfilment(List<OrderItem> acceptedServices) {
        return acceptedServices.stream().peek(OrderItem::markAsReceived).collect(Collectors.toList());
    }

    private List<OrderItem> runWarehouseFulfilment(Order order, List<OrderItem> acceptedProducts) {
        return warehouseFulfilmentService.run(order, acceptedProducts);
    }

    private List<OrderItem> runProvidersFulfilment(List<OrderItem> acceptedProducts) {
        return acceptedProducts.stream()
                .filter(orderItem -> !orderItem.isWarehouseFulfilled())
                .filter(Item::isReadyForAllocation)
                .peek(Item::markAsInAllocation)
                .collect(Collectors.toList());
    }

}
