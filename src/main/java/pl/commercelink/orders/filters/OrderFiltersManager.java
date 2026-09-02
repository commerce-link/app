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
                .sorted(Comparator.comparing(OrderFilter::isSharedWithStore).reversed()
                        .thenComparing(filter -> filter.getLabel() == null ? "" : filter.getLabel(),
                                String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public OrderFilter find(String storeId, String userId, String filterKey) {
        OrderFilter filter = orderFiltersRepository.findById(storeId, filterKey);
        return filter != null && filter.isVisibleTo(userId) ? filter : null;
    }

    public OrderFilter create(String storeId, String userId, boolean sharedWithStore, boolean administrator,
                              String label, List<String> rawConditions) {
        if (sharedWithStore && !administrator) {
            throw new OrderFilterAccessDeniedException("Only an administrator can create a filter shared with the store");
        }
        if (label == null || label.isBlank()) {
            throw new OrderFilterInvalidException("A filter needs a label");
        }

        OrderFilterConditions conditions = OrderFilterConditions.of(rawConditions);
        OrderFilter filter = sharedWithStore
                ? OrderFilter.sharedWithStore(storeId, label.trim(), conditions)
                : OrderFilter.ownedBy(storeId, userId, label.trim(), conditions);

        OrderFilter existing = orderFiltersRepository.findById(storeId, filter.getFilterKey());
        if (existing != null) {
            throw new OrderFilterInvalidException("The same filter already exists under the label " + existing.getLabel());
        }

        orderFiltersRepository.save(filter);
        return filter;
    }

    public void delete(String storeId, String userId, boolean administrator, String filterKey) {
        OrderFilter filter = orderFiltersRepository.findById(storeId, filterKey);
        if (filter == null) {
            return;
        }
        if (filter.isSharedWithStore() && !administrator) {
            throw new OrderFilterAccessDeniedException("Only an administrator can remove a filter shared with the store");
        }
        if (!filter.isSharedWithStore() && !filter.getScope().equals(userId)) {
            throw new OrderFilterAccessDeniedException("A private filter can be removed only by its owner");
        }
        orderFiltersRepository.delete(filter);
    }

    public List<Order> apply(List<Order> orders, OrderFilter filter) {
        return new OrderFilterMatcher(LocalDate.now()).apply(orders, filter);
    }
}
