package pl.commercelink.orders.filters;

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
        OrderFilterConditions conditions = filter.conditions();
        if (!conditions.isReadable()) {
            return false;
        }
        LocalDate today = LocalDate.now(clock);
        return conditions.conditions().stream().allMatch(condition -> condition.matches(order, today));
    }

    public List<Order> apply(List<Order> orders, OrderFilter filter) {
        return orders.stream().filter(order -> matches(order, filter)).toList();
    }
}
