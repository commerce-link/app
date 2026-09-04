package pl.commercelink.orders;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import pl.commercelink.orders.rma.RMA;
import pl.commercelink.orders.rma.RMAItem;
import pl.commercelink.orders.rma.RmaGoodsInService;
import pl.commercelink.documents.Document;
import pl.commercelink.starter.dynamodb.OptimisticLockingExecutor;
import pl.commercelink.starter.util.OperationResult;
import pl.commercelink.warehouse.api.ItemCondition;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Component
public class OrdersRMAManager {

    @Autowired
    private OrdersRepository ordersRepository;
    @Autowired
    private OrderItemsRepository orderItemsRepository;
    @Autowired
    private RmaGoodsInService rmaGoodsInService;
    @Autowired
    private OrderLifecycle orderLifecycle;
    @Autowired
    private OptimisticLockingExecutor optimisticLockingExecutor;
    @Autowired
    private OrderItemFamily orderItemFamily;

    public OperationResult<Document> acceptReturn(String storeId, RMA rma, List<RMAItem> rmaItems, ItemCondition condition) {
        Order order = ordersRepository.findById(storeId, rma.getOrderId());
        List<OrderItem> orderItems = orderItemsReachableFrom(order, rmaItems);
        List<OrderItem> newOrderItems = new ArrayList<>();

        Map<String, Double> totalToDecrementByOrderId = new HashMap<>();
        for (RMAItem rmaItem : rmaItems) {
            OrderItem originalItem = findOrderItemById(rmaItem.getItemId(), orderItems);

            OrderItem itemToProcess;
            if (originalItem.getQty() > rmaItem.getQty()) {
                itemToProcess = splitOrderItem(rmaItem, originalItem);
                newOrderItems.add(itemToProcess);
            } else {
                itemToProcess = originalItem;
            }

            totalToDecrementByOrderId.merge(itemToProcess.getOrderId(), itemToProcess.getTotalPrice(), Double::sum);

            // has to be done after computing total price
            itemToProcess.markAsReturned();
        }

        OperationResult<Document> op = rmaGoodsInService.receive(storeId, rma, rmaItems, order.getBillingDetails(), false, condition);
        commitCurrentOrderChangesIfSuccess(op, order, fresh -> {
            fresh.decreaseTotalPrice(totalToDecrementByOrderId.getOrDefault(order.getOrderId(), 0.0));
            fresh.reopen();
        }, orderItems, newOrderItems);
        adjustSplitOffOrders(op, storeId, order.getOrderId(), totalToDecrementByOrderId);
        return op;
    }

    public OperationResult<Document> createReplacementOrder(String storeId, RMA rma, List<RMAItem> rmaItems, boolean itemsRequireRepair, ItemCondition condition) {
        Order order = ordersRepository.findById(storeId, rma.getOrderId());
        List<OrderItem> orderItems = orderItemsReachableFrom(order, rmaItems);
        List<OrderItem> newOrderItems = new ArrayList<>();

        Order replacementOrder = new Order.Builder(order).build();
        List<OrderItem> replacementItems = new ArrayList<>();

        for (RMAItem rmaItem : rmaItems) {
            OrderItem originalItem = findOrderItemById(rmaItem.getItemId(), orderItems);

            OrderItem itemToProcess;
            if (originalItem.getQty() > rmaItem.getQty()) {
                itemToProcess = splitOrderItem(rmaItem, originalItem);
                newOrderItems.add(itemToProcess);
            } else {
                itemToProcess = originalItem;
            }
            itemToProcess.markAsReplaced();

            replacementItems.add(createReplacementItem(replacementOrder.getOrderId(), itemToProcess, rmaItem));
        }

        OperationResult<Document> op = rmaGoodsInService.receive(storeId, rma, rmaItems, order.getBillingDetails(), itemsRequireRepair, condition);
        commitCurrentOrderChangesIfSuccess(op, order, fresh -> { }, orderItems, newOrderItems);
        commitNewOrderChangesIfSuccess(op, replacementOrder, replacementItems);
        return op;
    }

    private void adjustSplitOffOrders(
            OperationResult<Document> op, String storeId, String parentOrderId, Map<String, Double> totalToDecrementByOrderId) {
        if (!op.isSuccess()) {
            return;
        }
        for (Map.Entry<String, Double> entry : totalToDecrementByOrderId.entrySet()) {
            if (entry.getKey().equals(parentOrderId)) {
                continue;
            }
            optimisticLockingExecutor.modifyAndSave(
                    () -> ordersRepository.findById(storeId, entry.getKey()),
                    fresh -> {
                        fresh.decreaseTotalPrice(entry.getValue());
                        fresh.reopen();
                    },
                    ordersRepository::save
            );
        }
    }

    private OrderItem splitOrderItem(RMAItem rmaItem, OrderItem originalItem) {
        int remainingQty = originalItem.getQty() - rmaItem.getQty();

        originalItem.setQty(remainingQty);
        originalItem.removeSerialNumbers(rmaItem.getSerialNo());

        OrderItem splitItem = originalItem.copyWithNewQty(rmaItem.getQty());
        splitItem.setSerialNo(rmaItem.getSerialNo());
        return splitItem;
    }

    private OrderItem findOrderItemById(String itemId, List<OrderItem> orderItems) {
        return orderItems.stream()
                .filter(i -> i.getItemId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Original order item not found for RMA item id: " + itemId));
    }

    /**
     * A marketplace return may have been matched against an item that Order.createSplit() moved to a child
     * order (see OrderItemFamily); the RMA still points at the parent, so the parent's own items are not
     * enough to resolve every RMA item. The family is read only when a plain lookup misses.
     */
    private List<OrderItem> orderItemsReachableFrom(Order order, List<RMAItem> rmaItems) {
        List<OrderItem> items = new ArrayList<>(orderItemsRepository.findByOrderId(order.getOrderId()));
        boolean allOnParent = rmaItems.stream()
                .map(RMAItem::getItemId)
                .allMatch(id -> items.stream().anyMatch(i -> i.getItemId().equals(id)));
        if (!allOnParent) {
            items.addAll(orderItemFamily.siblingItems(order));
        }
        return items;
    }

    private OrderItem createReplacementItem(String orderId, OrderItem orderItem, RMAItem rmaItem) {
        OrderItem replacementItem = new OrderItem(
                orderId,
                orderItem.getCategory(),
                orderItem.getName(),
                rmaItem.getQty(),
                0,
                orderItem.getSku(),
                orderItem.isConsolidated(),
                orderItem.getPosition()
        );
        replacementItem.setService(orderItem.isService());
        return replacementItem;
    }

    private void commitCurrentOrderChangesIfSuccess(
            OperationResult<Document> op, Order order, Consumer<Order> orderMutation,
            List<OrderItem> orderItems, List<OrderItem> newOrderItems) {
        if (op.isSuccess()) {
            orderItemsRepository.batchSave(orderItems);
            orderItemsRepository.batchSave(newOrderItems);

            optimisticLockingExecutor.modifyAndSave(
                    () -> ordersRepository.findById(order.getStoreId(), order.getOrderId()),
                    fresh -> {
                        orderMutation.accept(fresh);
                        if (op.hasPayload()) {
                            fresh.addDocumentIfMissing(op.getPayload());
                        }
                    },
                    ordersRepository::save
            );
        }
    }

    // oftentimes new order will go through adjustment so fulfilment has to be triggered manually
    private void commitNewOrderChangesIfSuccess(OperationResult<Document> op, Order order, List<OrderItem> orderItems) {
        if (op.isSuccess()) {
            ordersRepository.save(order);
            orderItemsRepository.batchSave(orderItems);
            orderLifecycle.update(order);
        }
    }

}
