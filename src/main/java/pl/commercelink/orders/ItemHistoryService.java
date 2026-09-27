package pl.commercelink.orders;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import pl.commercelink.inventory.deliveries.DeliveriesRepository;
import pl.commercelink.inventory.deliveries.Delivery;
import pl.commercelink.orders.rma.*;
import pl.commercelink.warehouse.api.Warehouse;
import pl.commercelink.warehouse.api.WarehouseItemView;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

@Service
public class ItemHistoryService {

    private static final Logger log = LoggerFactory.getLogger(ItemHistoryService.class);

    @Autowired
    private OrderItemsRepository orderItemsRepository;
    @Autowired
    private Warehouse warehouse;
    @Autowired
    private RMAItemsRepository rmaItemsRepository;
    @Autowired
    private OrdersRepository ordersRepository;
    @Autowired
    private DeliveriesRepository deliveriesRepository;
    @Autowired
    private RMARepository rmaRepository;

    public List<ItemHistoryEvent> getHistoryBySerial(String serialNo, String storeId) {
        List<ItemHistoryEvent> history = new ArrayList<>();

        // Order Items: the lookup scans every store, so only items whose order is in this store count
        List<OrderItem> orderItems = orderItemsRepository.findBySerialNo(serialNo);
        OrderItem firstOwnItem = null;
        if (orderItems != null) {
            for (OrderItem orderItem : orderItems) {
                // Related Order
                Order order = ordersRepository.findById(storeId, orderItem.getOrderId());
                if (order == null) {
                    log.warn("Item history {}: order {} not found in store {}, skipped", serialNo, orderItem.getOrderId(), storeId);
                    continue;
                }
                if (firstOwnItem == null) {
                    firstOwnItem = orderItem;
                }
                history.add(new ItemHistoryEvent(
                        order.getOrderId(),
                        order.getLastEventDate(),
                        "Order",
                        "Order Status: " + order.getStatus(),
                        "/dashboard/orders/" + order.getOrderId()
                ));
            }
        }

        // Related Delivery, of the first item that belongs to this store. Before a delivery exists the field may hold
        // something else (a supplier name): no delivery event then.
        if (firstOwnItem != null && isNotBlank(firstOwnItem.getDeliveryId())) {
            Delivery delivery = deliveriesRepository.findById(storeId, firstOwnItem.getDeliveryId());
            if (delivery == null) {
                log.warn("Item history {}: delivery {} not found in store {}, skipped", serialNo, firstOwnItem.getDeliveryId(), storeId);
            } else {
                history.add(new ItemHistoryEvent(
                        delivery.getDeliveryId(),
                        delivery.getOrderedAt(),
                        "Delivery",
                        "Delivery Created",
                        "/dashboard/deliveries/details?deliveryId=" + delivery.getDeliveryId()
                ));

                if (delivery.getReceivedAt() != null) {
                    history.add(new ItemHistoryEvent(
                            delivery.getDeliveryId(),
                            delivery.getReceivedAt(),
                            "Delivery",
                            "Delivery Received",
                            "/dashboard/deliveries/details?deliveryId=" + delivery.getDeliveryId()
                    ));
                }
            }
        }

        // RMA Item
        List<RMAItem> rmaItems = rmaItemsRepository.findBySerialNo(serialNo);
        boolean movedToWarehouse = false;

        if (rmaItems != null && !rmaItems.isEmpty()) {
            for (RMAItem rmaItem : rmaItems) {
                RMA rma = rmaRepository.findById(storeId, rmaItem.getRmaId());
                if (rma == null) {
                    log.warn("Item history {}: RMA {} not found in store {}, skipped", serialNo, rmaItem.getRmaId(), storeId);
                    continue;
                }
                String actualResolution = (rmaItem.getActualResolution() != null)
                        ? rmaItem.getActualResolution().toString()
                        : "N/A";

                history.add(new ItemHistoryEvent(
                        rma.getRmaId(),
                        rma.getCreatedAt(),
                        "RMA",
                        "Status: " + rmaItem.getStatus()
                                + ", Desired Resolution: " + rmaItem.getDesiredResolution()
                                + ", Actual Resolution: " + actualResolution,
                        "/dashboard/rma/" + rma.getRmaId()
                ));

                if (rmaItem.getStatus() == RMAItemStatus.MovedToWarehouse) {
                    movedToWarehouse = true;
                }
            }
        }

        // an order without any date yet must not break the sort
        history.sort(Comparator.comparing(ItemHistoryEvent::getDate, Comparator.nullsLast(Comparator.naturalOrder())));

        // Warehouse Item
        WarehouseItemView warehouseItem = warehouse.stockQueryService(storeId).findBySerialNo(storeId, serialNo);
        if (warehouseItem != null) {
            String status = warehouseItem.isInStock() ? "In Stock" : "In RMA or Destroyed";

            ItemHistoryEvent warehouseEvent = new ItemHistoryEvent(
                    warehouseItem.getItemId(),
                    null,
                    "Warehouse",
                    "Warehouse Item Status: " + status,
                    "/dashboard/warehouse/items/" + warehouseItem.getItemId()
            );

            if (movedToWarehouse) {
                history.add(warehouseEvent);
            } else {
                int rmaIndex = -1;
                for (int i = 0; i < history.size(); i++) {
                    if ("RMA".equals(history.get(i).getSource())) {
                        rmaIndex = i;
                        break;
                    }
                }
                if (rmaIndex >= 0) {
                    history.add(rmaIndex, warehouseEvent);
                } else {
                    history.add(warehouseEvent);
                }
            }
        }

        return history;
    }
}
