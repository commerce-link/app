package pl.commercelink.orders;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import pl.commercelink.orders.rma.RMA;
import pl.commercelink.orders.rma.RMAItem;
import pl.commercelink.orders.rma.RmaGoodsInService;
import pl.commercelink.documents.Document;
import pl.commercelink.starter.util.OperationResult;

import java.util.ArrayList;
import java.util.List;

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

    public OperationResult<Document> acceptReturn(String storeId, RMA rma, List<RMAItem> rmaItems) {
        Order order = ordersRepository.findById(storeId, rma.getOrderId());
        List<OrderItem> orderItems = orderItemsRepository.findByOrderId(order.getOrderId());
        List<OrderItem> newOrderItems = new ArrayList<>();

        for (RMAItem rmaItem : rmaItems) {
            OrderItem originalItem = findOrderItemById(rmaItem.getItemId(), orderItems);

            OrderItem itemToProcess;
            if (originalItem.getQty() > rmaItem.getQuantity()) {
                itemToProcess = splitOrderItem(rmaItem, originalItem);
                newOrderItems.add(itemToProcess);
            } else {
                itemToProcess = originalItem;
            }

            order.decreaseTotalPrice(itemToProcess.getTotalPrice());
            order.reopen();

            // has to be done after decreasing total price and reopening the order
            itemToProcess.markAsReturned();
        }

        OperationResult<Document> op = rmaGoodsInService.receive(storeId, rma, rmaItems, order.getBillingDetails(), false);
        commitCurrentOrderChangesIfSuccess(op, order, orderItems, newOrderItems);
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
            if (originalItem.getQty() > rmaItem.getQuantity()) {
                itemToProcess = splitOrderItem(rmaItem, originalItem);
                newOrderItems.add(itemToProcess);
            } else {
                itemToProcess = originalItem;
            }
            itemToProcess.markAsReplaced();

            replacementItems.add(createReplacementItem(replacementOrder.getOrderId(), itemToProcess, rmaItem));
        }

        OperationResult<Document> op = rmaGoodsInService.receive(storeId, rma, rmaItems, order.getBillingDetails(), itemsRequireRepair);
        commitCurrentOrderChangesIfSuccess(op, order, orderItems, newOrderItems);
        commitNewOrderChangesIfSuccess(op, replacementOrder, replacementItems);
        return op;
    }

    private OrderItem splitOrderItem(RMAItem rmaItem, OrderItem originalItem) {
        int remainingQty = originalItem.getQty() - rmaItem.getQuantity();

        originalItem.setQty(remainingQty);
        originalItem.removeSerialNumbers(rmaItem.getSerialNo());

        OrderItem splitItem = originalItem.copyWithNewQty(rmaItem.getQuantity());
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
                orderItem.getCategory(),
                orderItem.getName(),
                rmaItem.getQuantity(),
                0,
                orderItem.getSku(),
                orderItem.isConsolidated()
        );
    }

    private void commitCurrentOrderChangesIfSuccess(
            OperationResult<Document> op, Order order, List<OrderItem> orderItems, List<OrderItem> newOrderItems) {
        if (op.isSuccess()) {
            if (op.hasPayload()) {
                order.addDocument(op.getPayload());
            }

            orderItemsRepository.batchSave(orderItems);
            orderItemsRepository.batchSave(newOrderItems);
            ordersRepository.save(order);
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
