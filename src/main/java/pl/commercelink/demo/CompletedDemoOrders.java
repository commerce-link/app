package pl.commercelink.demo;

import pl.commercelink.inventory.deliveries.Delivery;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.orders.event.OrderEvent;
import pl.commercelink.orders.rma.RMA;
import pl.commercelink.orders.rma.RMAItem;
import pl.commercelink.warehouse.builtin.WarehouseDocument;
import pl.commercelink.warehouse.builtin.WarehouseDocumentItem;
import pl.commercelink.warehouse.builtin.WarehouseDocumentSequence;

import java.util.List;
import java.util.Map;

record CompletedDemoOrders(List<Order> orders,
                           Map<String, List<OrderItem>> itemsByOrderId,
                           List<Delivery> deliveries,
                           List<WarehouseDocument> documents,
                           List<WarehouseDocumentItem> documentItems,
                           List<WarehouseDocumentSequence> sequences,
                           List<OrderEvent> events,
                           RMA rma,
                           List<RMAItem> rmaItems) {
}
