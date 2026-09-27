package pl.commercelink.orders;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Turns what an operator types as "the other order" into an order of the store: a full number, the short number the
 * list shows (a prefix of the id) or the marketplace's number. A number shared by several orders is ambiguous.
 */
@Component
@RequiredArgsConstructor
public class OrderReferenceResolver {

    public static final int MIN_SHORT_ID = 4;
    public static final int MAX_REFERENCE = 64;

    private static final Pattern FULL_ID =
            Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    private static final Pattern HEX_PREFIX = Pattern.compile("[0-9a-fA-F-]+");

    private final OrdersRepository ordersRepository;

    public Resolution resolve(String storeId, String reference) {
        String value = StringUtils.trimToEmpty(reference);
        // DynamoDB rejects a key condition over 1024 bytes with a 500; nothing an operator types is that long
        if (value.isEmpty() || value.length() > MAX_REFERENCE) {
            return Resolution.notFound();
        }
        if (FULL_ID.matcher(value).matches()) {
            Order order = ordersRepository.findById(storeId, value.toLowerCase(Locale.ROOT));
            return order == null ? Resolution.notFound() : Resolution.found(order);
        }
        if (value.length() >= MIN_SHORT_ID && HEX_PREFIX.matcher(value).matches()) {
            List<Order> byPrefix = ordersRepository.findByShortId(storeId, value.toLowerCase(Locale.ROOT));
            if (byPrefix.size() == 1) {
                return Resolution.found(byPrefix.get(0));
            }
            if (byPrefix.size() > 1) {
                return Resolution.ambiguous(byPrefix.size());
            }
        }
        if (value.length() < MIN_SHORT_ID) {
            return Resolution.notFound();
        }
        List<Order> hits = ordersRepository.findAllByStoreIdAndExternalOrderId(storeId, value);
        if (hits.size() > 1) {
            return Resolution.ambiguous(hits.size());
        }
        // the index returns a skeleton (keys and orderId only), so the preview reads the order itself
        Order order = hits.isEmpty() ? null : ordersRepository.findById(storeId, hits.get(0).getOrderId());
        return order == null ? Resolution.notFound() : Resolution.found(order);
    }

    public enum Outcome { FOUND, NOT_FOUND, AMBIGUOUS }

    public record Resolution(Order order, Outcome outcome, int candidates) {

        public static Resolution found(Order order) {
            return new Resolution(order, Outcome.FOUND, 1);
        }

        public static Resolution notFound() {
            return new Resolution(null, Outcome.NOT_FOUND, 0);
        }

        public static Resolution ambiguous(int candidates) {
            return new Resolution(null, Outcome.AMBIGUOUS, candidates);
        }

        public boolean isFound() {
            return outcome == Outcome.FOUND;
        }
    }
}
