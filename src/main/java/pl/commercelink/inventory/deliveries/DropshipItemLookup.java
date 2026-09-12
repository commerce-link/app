package pl.commercelink.inventory.deliveries;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.orders.OrderItem;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Reads how an order's goods travel off the deliveries behind its items - per item and for the order as a
 * whole. Items sitting in a dropship delivery never reach the warehouse, so every action that would move
 * them there (return to stock, RMA shortcut, quantity edits) must leave them alone; and an order whose every
 * item is on such a delivery never sees in-house handling at all.
 */
@Component
@RequiredArgsConstructor
public class DropshipItemLookup {

    private final DeliveriesRepository deliveriesRepository;

    public Set<String> itemIdsInDropshipDeliveries(String storeId, List<OrderItem> orderItems) {
        Map<String, Boolean> dropshipByDeliveryId = new HashMap<>();
        Set<String> itemIds = new HashSet<>();
        for (OrderItem item : orderItems) {
            if (!item.isAllocated() || item.getDeliveryId() == null
                    || SupplierRegistry.WAREHOUSE.equalsIgnoreCase(item.getDeliveryId())) {
                continue;
            }
            boolean dropship = dropshipByDeliveryId.computeIfAbsent(item.getDeliveryId(), deliveryId -> {
                Delivery delivery = deliveriesRepository.findById(storeId, deliveryId);
                return delivery != null && delivery.isDropship();
            });
            if (dropship) {
                itemIds.add(item.getItemId());
            }
        }
        return itemIds;
    }

    /**
     * Does the whole order travel straight from its suppliers to the customer? Any leg through our warehouse
     * has to be picked, packed and forwarded by hand, which is what the order's realization days pay for, so
     * only an order whose every item is on a dropship delivery skips them. The question is deliberately asked
     * of the order and not of a single delivery: one delivery cannot see the other legs, and the order's
     * fulfilment type cannot see the route either - a direct-to-consumer order can still be served from a
     * warehouse delivery.
     */
    public boolean isEntirelyDropship(String storeId, List<OrderItem> orderItems) {
        return routeOf(storeId, orderItems).entirelyDropship();
    }

    /**
     * The deliveries behind the order's goods, together with the answer to the route question. The two are
     * produced in one pass so that a caller needing both - the latest arrival date and the route - cannot end
     * up asking them of different item sets.
     *
     * <p>Every item carrying goods has to be accounted for. An item whose delivery does not resolve - one
     * still waiting to be bought and carrying only its supplier's name, one taken from our own shelf, one on
     * a delivery that has since been deleted - is not evidence of a dropship route; it is the absence of
     * evidence, and it is exactly the item somebody will have to pick and pack. Such an order is not entirely
     * dropship. Services carry no goods and are ignored, and an order with no goods at all answers no rather
     * than reading an empty match as "travels entirely by dropship".
     *
     * <p>The reads are consistent on purpose: the ordering path saves a delivery and asks this question about
     * it in the same request, and an eventually consistent replica answering "no such delivery" would drop a
     * leg from the route and silently flip the answer.
     */
    public GoodsRoute routeOf(String storeId, List<OrderItem> orderItems) {
        List<OrderItem> goods = orderItems.stream().filter(OrderItem::isProduct).toList();
        Map<String, Delivery> deliveriesById = new LinkedHashMap<>();
        for (OrderItem item : goods) {
            if (StringUtils.isNotBlank(item.getDeliveryId())) {
                deliveriesById.computeIfAbsent(item.getDeliveryId(),
                        deliveryId -> deliveriesRepository.findByIdConsistently(storeId, deliveryId));
            }
        }

        boolean entirelyDropship = !goods.isEmpty() && goods.stream().allMatch(item -> {
            Delivery delivery = deliveriesById.get(item.getDeliveryId());
            return delivery != null && delivery.isDropship();
        });

        return new GoodsRoute(
                deliveriesById.values().stream().filter(Objects::nonNull).toList(),
                entirelyDropship);
    }

    /**
     * @param deliveries       the distinct, existing deliveries behind the order's goods
     * @param entirelyDropship whether every one of those goods travels straight from a supplier to the
     *                         customer, so the order owes no in-house handling time
     */
    public record GoodsRoute(List<Delivery> deliveries, boolean entirelyDropship) {
    }
}
