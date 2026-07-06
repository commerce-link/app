package pl.commercelink.orders;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import pl.commercelink.orders.rma.RMA;
import pl.commercelink.orders.rma.RMAItem;
import pl.commercelink.orders.rma.RmaGoodsInService;
import pl.commercelink.documents.Document;
import pl.commercelink.starter.dynamodb.OptimisticLockingExecutor;
import pl.commercelink.starter.util.OperationResult;

import java.util.ArrayList;
import java.util.List;
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

    public OperationResult<Document> acceptReturn(String storeId, RMA rma, List<RMAItem> rmaItems) {
        Order order = ordersRepository.findById(storeId, rma.getOrderId());
        List<OrderItem> orderItems = orderItemsRepository.findByOrderId(order.getOrderId());
        List<OrderItem> newOrderItems = new ArrayList<>();

        double totalToDecrement = 0;
        for (RMAItem rmaItem : rmaItems) {
            OrderItem originalItem = findOrderItemById(rmaItem.getItemId(), orderItems);

            OrderItem itemToProcess;
            if (originalItem.getQty() > rmaItem.getQty()) {
                itemToProcess = splitOrderItem(rmaItem, originalItem);
                newOrderItems.add(itemToProcess);
            } else {
                itemToProcess = originalItem;
            }

            totalToDecrement += itemToProcess.getTotalPrice();

            // has to be done after computing total price
            itemToProcess.markAsReturned();
        }

        double finalTotalToDecrement = totalToDecrement;
        OperationResult<Document> op = rmaGoodsInService.receive(storeId, rma, rmaItems, order.getBillingDetails(), false);
        commitCurrentOrderChangesIfSuccess(op, order, fresh -> {
            fresh.decreaseTotalPrice(finalTotalToDecrement);
            fresh.reopen();
        }, orderItems, newOrderItems);
        return op;
    }

    public OperationResult<Document> createReplacementOrder(String storeId, RMA rma, List<RMAItem> rmaItems, boolean itemsRequireRepair) {
        Order order = ordersRepository.findById(storeId, rma.getOrderId());
        List<OrderItem> orderItems = orderItemsRepository.findByOrderId(order.getOrderId());
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

        OperationResult<Document> op = rmaGoodsInService.receive(storeId, rma, rmaItems, order.getBillingDetails(), itemsRequireRepair);
        commitCurrentOrderChangesIfSuccess(op, order, fresh -> { }, orderItems, newOrderItems);
        commitNewOrderChangesIfSuccess(op, replacementOrder, replacementItems);
        return op;
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

    private OrderItem createReplacementItem(String orderId, OrderItem orderItem, RMAItem rmaItem) {
        return new OrderItem(
                orderId,
                orderItem.getCategoryKey(),
                orderItem.getName(),
                rmaItem.getQty(),
                0,
                orderItem.getSku(),
                orderItem.isConsolidated(),
                orderItem.getPosition()
        );
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
