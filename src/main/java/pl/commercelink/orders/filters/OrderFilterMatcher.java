package pl.commercelink.orders.filters;

import pl.commercelink.orders.filters.model.OrderFilter;
import pl.commercelink.orders.filters.model.OrderFilterConditionSerializer;

import org.springframework.stereotype.Component;
import pl.commercelink.orders.Order;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

@Component
public class OrderFilterMatcher {

    private final Clock clock;

    public OrderFilterMatcher() {
        this(Clock.systemDefaultZone());
    }

    public OrderFilterMatcher(Clock clock) {
        this.clock = clock;
    }

    public boolean matches(Order order, OrderFilter filter) {
        if (filter == null) {
            return true;
        }
        LocalDate today = LocalDate.now(clock);
        return OrderFilterConditionSerializer.fromStoredEntries(filter.getConditions())
                .map(conditions -> conditions.matchedBy(condition -> condition.matches(order, today)))
                .orElse(false);
    }

    public List<Order> apply(List<Order> orders, OrderFilter filter) {
        return orders.stream().filter(order -> matches(order, filter)).toList();
    }
}
