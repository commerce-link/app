package pl.commercelink.orders;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Turns what an operator types as "the other order" into an order of the store: a full number, the short number the
 * list shows (a prefix of the id) or the marketplace's number. A short number shared by several orders is ambiguous.
 */
@Component
@RequiredArgsConstructor
public class OrderReferenceResolver {

    public static final int MIN_SHORT_ID = 4;

    private static final Pattern FULL_ID =
            Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    private static final Pattern HEX_PREFIX = Pattern.compile("[0-9a-fA-F-]+");

    private final OrdersRepository ordersRepository;

    public Resolution resolve(String storeId, String reference) {
        String value = StringUtils.trimToEmpty(reference);
        if (value.isEmpty()) {
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
        Order external = ordersRepository.findByStoreIdAndExternalOrderId(storeId, value);
        return external == null ? Resolution.notFound() : Resolution.found(external);
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
