package pl.commercelink.orders.filters;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import pl.commercelink.orders.Order;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

@Component
public class OrderFiltersManager {

    @Autowired
    private OrderFiltersRepository orderFiltersRepository;

    public List<OrderFilter> visibleTo(String storeId, String userId) {
        return orderFiltersRepository.findAllByStoreId(storeId).stream()
                .filter(filter -> filter.isVisibleTo(userId))
                .sorted(Comparator.comparing(OrderFilter::isGlobal).reversed()
                        .thenComparing(filter -> filter.getName() == null ? "" : filter.getName(), String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public OrderFilter find(String storeId, String userId, String filterKey) {
        OrderFilter filter = orderFiltersRepository.findById(storeId, filterKey);
        return filter != null && filter.isVisibleTo(userId) ? filter : null;
    }

    public OrderFilter create(String storeId, String userId, boolean global, boolean administrator, String name, List<OrderFilterCondition> conditions) {
        if (global && !administrator) {
            throw new OrderFilterAccessDeniedException("Only an administrator can create a store wide filter");
        }

        OrderFilter filter = global
                ? OrderFilter.global(storeId, name, conditions)
                : OrderFilter.ownedBy(storeId, userId, name, conditions);

        orderFiltersRepository.save(filter);
        return filter;
    }

    public void delete(String storeId, String userId, boolean administrator, String filterKey) {
        OrderFilter filter = orderFiltersRepository.findById(storeId, filterKey);
        if (filter == null) {
            return;
        }
        if (filter.isGlobal() && !administrator) {
            throw new OrderFilterAccessDeniedException("Only an administrator can remove a store wide filter");
        }
        if (!filter.isGlobal() && !filter.getOwner().equals(userId)) {
            throw new OrderFilterAccessDeniedException("A private filter can be removed only by its owner");
        }
        orderFiltersRepository.delete(filter);
    }

    public List<Order> apply(List<Order> orders, OrderFilter filter) {
        return new OrderFilterMatcher(LocalDate.now()).apply(orders, filter);
    }
}
