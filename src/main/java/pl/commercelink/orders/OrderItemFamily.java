package pl.commercelink.orders;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Order.createSplit() moves an OrderItem to a new child Order and deletes it from the parent (and never
 * copies externalOrderId, which must stay unique per store), so an item moved to a split-off order is no
 * longer reachable from the marketplace order itself. This resolves the order items living on orders split
 * off from a given order, so marketplace-return matching (which item?) and refunding (which key?) can still
 * find an item that moved. Shared by MarketplaceReturnImporter and MarketplaceReturnDecisions
 * so the family concept and the cancelled-child exclusion live in exactly one place.
 *
 * <p>Reading the family is comparatively expensive: {@link OrdersRepository#findBySplitFromOrderId} queries
 * the store's whole Orders partition (with a filter, not a key condition, on splitFromOrderId). Callers
 * should only reach for {@link #siblingItems} after a lookup against the order's own items misses, not
 * eagerly on every return.
 */
@Component
@RequiredArgsConstructor
public class OrderItemFamily {

    private final OrdersRepository ordersRepository;
    private final OrderItemsRepository orderItemsRepository;

    /**
     * Items of every order split off from {@code order}, excluding cancelled children — a cancelled order's
     * items were never fulfilled towards the buyer and must not become matchable or count towards coverage.
     */
    public List<OrderItem> siblingItems(Order order) {
        List<OrderItem> items = new ArrayList<>();
        for (Order sibling : ordersRepository.findBySplitFromOrderId(order.getStoreId(), order.getOrderId())) {
            if (sibling.getStatus() == OrderStatus.Cancelled) {
                continue;
            }
            items.addAll(orderItemsRepository.findByOrderId(sibling.getOrderId()));
        }
        return items;
    }
}
