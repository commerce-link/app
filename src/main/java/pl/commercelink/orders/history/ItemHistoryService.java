package pl.commercelink.orders.history;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import pl.commercelink.inventory.deliveries.DeliveriesRepository;
import pl.commercelink.inventory.deliveries.Delivery;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.orders.rma.RMA;
import pl.commercelink.orders.rma.RMAItem;
import pl.commercelink.orders.rma.RMARepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

@Slf4j
@Service
@RequiredArgsConstructor
public class ItemHistoryService {

    private final SerialNumberLookup lookup;
    private final OrdersRepository ordersRepository;
    private final DeliveriesRepository deliveriesRepository;
    private final RMARepository rmaRepository;

    public ItemHistory history(String storeId, String serialNo) {
        String wanted = serialNo.trim();
        SerialNumberMatches matches = lookup.find(storeId, wanted);
        List<OrderLine> orders = orderLines(storeId, wanted, matches.orderItems());
        List<RmaLine> rmas = rmaLines(storeId, wanted, matches.rmaItems());

        List<ItemHistoryEvent> events = new ArrayList<>();
        orders.forEach(line -> events.add(ItemHistoryEvent.orderPlaced(line)));
        rmas.forEach(line -> events.add(ItemHistoryEvent.rmaCreated(line)));
        for (Delivery delivery : deliveries(storeId, orders)) {
            events.add(ItemHistoryEvent.deliveryOrdered(delivery));
            if (delivery.getReceivedAt() != null) {
                events.add(ItemHistoryEvent.deliveryReceived(delivery));
            }
        }
        events.sort(ItemHistoryEvent.NEWEST_FIRST);

        boolean found = !orders.isEmpty() || !rmas.isEmpty() || !matches.warehouseItems().isEmpty();
        ItemIdentity identity = found ? ItemIdentity.of(orders, rmas, matches.warehouseItems()) : null;
        return new ItemHistory(wanted, found, identity, ItemAmbiguity.of(orders, identity),
                ItemNow.resolve(orders, rmas, matches.warehouseItems()),
                List.copyOf(events.subList(0, Math.min(events.size(), ItemHistory.EVENT_LIMIT))), events.size());
    }

    // Order items carry no store id: an item belongs here when its order loads in this store.
    private List<OrderLine> orderLines(String storeId, String serialNo, List<OrderItem> items) {
        Map<String, Order> orders = byId(ordersRepository.findAllByIds(storeId, ids(items, OrderItem::getOrderId)), Order::getOrderId);
        List<OrderLine> lines = new ArrayList<>();
        for (OrderItem item : items) {
            Order order = orders.get(item.getOrderId());
            if (order == null) {
                log.warn("Item history {}: order {} not found in store {}, skipped", serialNo, item.getOrderId(), storeId);
                continue;
            }
            lines.add(new OrderLine(order, item));
        }
        return lines;
    }

    private List<RmaLine> rmaLines(String storeId, String serialNo, List<RMAItem> items) {
        Map<String, RMA> rmas = byId(rmaRepository.findAllByIds(storeId, ids(items, RMAItem::getRmaId)), RMA::getRmaId);
        List<RmaLine> lines = new ArrayList<>();
        for (RMAItem item : items) {
            RMA rma = rmas.get(item.getRmaId());
            if (rma == null) {
                log.warn("Item history {}: RMA {} not found in store {}, skipped", serialNo, item.getRmaId(), storeId);
                continue;
            }
            lines.add(new RmaLine(rma, item));
        }
        return lines;
    }

    // Before a delivery exists the item's deliveryId may hold a supplier name: it loads as nothing and is left out.
    private List<Delivery> deliveries(String storeId, List<OrderLine> orders) {
        List<String> ids = orders.stream().map(l -> l.item().getDeliveryId()).filter(id -> isNotBlank(id)).distinct().toList();
        return deliveriesRepository.findAllByIds(storeId, ids);
    }

    private static <T> List<String> ids(List<T> items, Function<T, String> id) {
        return items.stream().map(id).filter(Objects::nonNull).distinct().toList();
    }

    private static <T> Map<String, T> byId(List<T> records, Function<T, String> id) {
        return records.stream().collect(Collectors.toMap(id, Function.identity(), (a, b) -> a));
    }
}
