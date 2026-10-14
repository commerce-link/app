package pl.commercelink.orders.rma;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.orders.OrderItemsRepository;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.orders.SerialNumbers;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Turns what the operator typed on the new RMA page into an order: an e-mail, a full order id, the first characters
 * of one, or an item's serial number — always within the session store. Order item ids carry no store id, so a serial
 * number only counts when its order loads in this store.
 */
@Component
@RequiredArgsConstructor
public class RmaOrderSearch {

    public static final int CHOICE_LIMIT = 50;
    public static final int MIN_PREFIX = 8;
    // far above any e-mail, order id or serial number; a longer key would break DynamoDB's 1024-byte sort-key limit
    public static final int MAX_QUERY = 200;
    private static final Pattern PREFIX = Pattern.compile("[0-9a-f-]{" + MIN_PREFIX + ",}");

    private final OrdersRepository orders;
    private final OrderItemsRepository orderItems;

    public enum Kind { EMPTY, ORDER, CHOICE, NOT_FOUND }

    public record Result(Kind kind, String query, boolean byEmail, Order order, String serialItemId, List<Order> orders,
                         boolean truncated) {

        static Result empty() {
            return new Result(Kind.EMPTY, null, false, null, null, List.of(), false);
        }

        static Result notFound(String query, boolean byEmail) {
            return new Result(Kind.NOT_FOUND, query, byEmail, null, null, List.of(), false);
        }

        static Result order(String query, boolean byEmail, Order order, String serialItemId) {
            return new Result(Kind.ORDER, query, byEmail, order, serialItemId, List.of(), false);
        }
    }

    public Result search(String storeId, String query) {
        String q = StringUtils.trimToNull(query);
        if (q == null) {
            return Result.empty();
        }
        if (q.length() > MAX_QUERY) {
            return Result.notFound(q, q.contains("@"));
        }
        if (q.contains("@")) {
            List<String> ids = orders.findOrderIdsByEmail(storeId, q, CHOICE_LIMIT + 1);
            boolean truncated = ids.size() > CHOICE_LIMIT;
            List<Order> found = ids.isEmpty() ? List.of() : orders.findByIds(storeId, ids.subList(0, Math.min(ids.size(), CHOICE_LIMIT)));
            return choose(q, true, found, null, truncated);
        }
        Order exact = orders.findById(storeId, q);
        if (exact != null) {
            return Result.order(q, false, exact, null);
        }
        String lower = q.toLowerCase(Locale.ROOT);
        if (PREFIX.matcher(lower).matches()) {
            List<Order> byPrefix = orders.findByShortId(storeId, lower);
            if (!byPrefix.isEmpty()) {
                return choose(q, false, byPrefix, null, false);
            }
        }
        // only order items: an RMA item or a warehouse unit with that number would add nothing to open on this page
        List<OrderItem> items = orderItems.findBySerialNo(q).stream()
                .filter(i -> SerialNumbers.contains(i.getSerialNo(), q))
                .toList();
        if (items.isEmpty()) {
            return Result.notFound(q, false);
        }
        List<Order> withSerial = orders.findByIds(storeId, List.copyOf(new LinkedHashSet<>(items.stream().map(OrderItem::getOrderId).toList())));
        return choose(q, false, withSerial, items, false);
    }

    private Result choose(String q, boolean byEmail, List<Order> found, List<OrderItem> serialItems, boolean truncated) {
        if (found.isEmpty()) {
            return Result.notFound(q, byEmail);
        }
        List<Order> eligible = found.stream().filter(Order::isEligibleForRMACreation).toList();
        // a truncated list holds only the newest orders, so its one eligible order may not be the only one
        Order only = found.size() == 1 ? found.get(0) : eligible.size() == 1 && !truncated ? eligible.get(0) : null;
        if (only != null) {
            return Result.order(q, byEmail, only, serialItemOf(only, serialItems));
        }
        List<Order> newestFirst = found.stream()
                .sorted(Comparator.comparing(Order::getOrderedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        return new Result(Kind.CHOICE, q, byEmail, null, null, newestFirst, truncated);
    }

    private static String serialItemOf(Order order, List<OrderItem> serialItems) {
        if (serialItems == null) {
            return null;
        }
        return serialItems.stream().filter(i -> order.getOrderId().equals(i.getOrderId()))
                .map(OrderItem::getItemId).findFirst().orElse(null);
    }
}
