package pl.commercelink.orders.filters.handlers;

import org.springframework.stereotype.Component;
import pl.commercelink.orders.filters.FilterActor;
import pl.commercelink.orders.filters.model.OrderFilter;
import pl.commercelink.orders.filters.OrderFiltersRepository;
import pl.commercelink.orders.filters.VisibleOrderFilters;

import java.util.Comparator;
import java.util.List;

@Component
public class ListOrderFiltersHandler {

    private final OrderFiltersRepository orderFiltersRepository;

    public ListOrderFiltersHandler(OrderFiltersRepository orderFiltersRepository) {
        this.orderFiltersRepository = orderFiltersRepository;
    }

    public VisibleOrderFilters handle(FilterActor actor) {
        List<OrderFilter> visible = orderFiltersRepository.findAllByStoreId(actor.storeId()).stream()
                .filter(filter -> filter.isVisibleTo(actor.userId()))
                .sorted(Comparator.comparing(OrderFilter::isSharedWithStore).reversed()
                        .thenComparing(filter -> filter.getLabel() == null ? "" : filter.getLabel(),
                                String.CASE_INSENSITIVE_ORDER))
                .toList();
        return new VisibleOrderFilters(visible);
    }
}
