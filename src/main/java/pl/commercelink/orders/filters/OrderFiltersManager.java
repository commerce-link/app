package pl.commercelink.orders.filters;

import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
public class OrderFiltersManager {

    private final OrderFiltersRepository orderFiltersRepository;

    public OrderFiltersManager(OrderFiltersRepository orderFiltersRepository) {
        this.orderFiltersRepository = orderFiltersRepository;
    }

    public List<OrderFilter> visibleTo(FilterActor actor) {
        return orderFiltersRepository.findAllByStoreId(actor.storeId()).stream()
                .filter(filter -> filter.isVisibleTo(actor.userId()))
                .sorted(Comparator.comparing(OrderFilter::isSharedWithStore).reversed()
                        .thenComparing(filter -> filter.getLabel() == null ? "" : filter.getLabel(),
                                String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public OrderFilter find(FilterActor actor, String filterKey) {
        OrderFilter filter = orderFiltersRepository.findById(actor.storeId(), filterKey);
        return filter != null && filter.isVisibleTo(actor.userId()) ? filter : null;
    }

    public OrderFilter create(FilterActor actor, boolean sharedWithStore, String label,
                              List<OrderFilterCondition> conditions) {
        if (sharedWithStore && !actor.administrator()) {
            throw new OrderFilterAccessDeniedException("Only an administrator can create a filter shared with the store");
        }
        String trimmedLabel = requireLabel(label);

        OrderFilterConditions filterConditions = OrderFilterConditions.of(conditions);
        OrderFilter filter = sharedWithStore
                ? OrderFilter.sharedWithStore(actor.storeId(), trimmedLabel, filterConditions)
                : OrderFilter.ownedBy(actor.storeId(), actor.userId(), trimmedLabel, filterConditions);

        requireNoClash(actor.storeId(), filter.getFilterKey());
        orderFiltersRepository.save(filter);
        return filter;
    }

    public OrderFilter update(FilterActor actor, String filterKey, String label,
                              List<OrderFilterCondition> conditions) {
        OrderFilter existing = orderFiltersRepository.findById(actor.storeId(), filterKey);
        if (existing == null) {
            throw new OrderFilterInvalidException("The filter no longer exists");
        }
        requireWriteAccess(existing, actor);
        String trimmedLabel = requireLabel(label);

        OrderFilter replacement = existing.withConditions(trimmedLabel, OrderFilterConditions.of(conditions));

        if (replacement.getFilterKey().equals(existing.getFilterKey())) {
            existing.setLabel(trimmedLabel);
            orderFiltersRepository.save(existing);
            return existing;
        }

        requireNoClash(actor.storeId(), replacement.getFilterKey());
        orderFiltersRepository.save(replacement);
        orderFiltersRepository.delete(existing);
        return replacement;
    }

    public void delete(FilterActor actor, String filterKey) {
        OrderFilter filter = orderFiltersRepository.findById(actor.storeId(), filterKey);
        if (filter == null) {
            return;
        }
        requireWriteAccess(filter, actor);
        orderFiltersRepository.delete(filter);
    }

    private static String requireLabel(String label) {
        if (label == null || label.isBlank()) {
            throw new OrderFilterInvalidException("A filter needs a label");
        }
        return label.trim();
    }

    private void requireNoClash(String storeId, String filterKey) {
        OrderFilter clash = orderFiltersRepository.findById(storeId, filterKey);
        if (clash != null) {
            throw new OrderFilterInvalidException("The same filter already exists under the label " + clash.getLabel());
        }
    }

    private static void requireWriteAccess(OrderFilter filter, FilterActor actor) {
        if (filter.isSharedWithStore() && !actor.administrator()) {
            throw new OrderFilterAccessDeniedException("Only an administrator can change a filter shared with the store");
        }
        if (!filter.isSharedWithStore() && !filter.getScope().equals(actor.userId())) {
            throw new OrderFilterAccessDeniedException("A private filter can be changed only by its owner");
        }
    }
}
