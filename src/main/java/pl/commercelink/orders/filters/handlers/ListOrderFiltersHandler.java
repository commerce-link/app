package pl.commercelink.orders.filters.handlers;

import org.springframework.stereotype.Component;
import pl.commercelink.orders.filters.FilterActor;
import pl.commercelink.orders.filters.OrderFiltersRepository;
import pl.commercelink.orders.filters.VisibleOrderFilters;
import pl.commercelink.orders.filters.model.OrderFilter;
import pl.commercelink.orders.filters.model.OwnedOrderFilters;

import java.util.List;

@Component
public class ListOrderFiltersHandler {

    private final OrderFiltersRepository orderFiltersRepository;

    public ListOrderFiltersHandler(OrderFiltersRepository orderFiltersRepository) {
        this.orderFiltersRepository = orderFiltersRepository;
    }

    public VisibleOrderFilters handle(FilterActor actor) {
        return new VisibleOrderFilters(
                filtersOf(actor.storeId(), OwnedOrderFilters.WHOLE_STORE),
                filtersOf(actor.storeId(), actor.userId()));
    }

    private List<OrderFilter> filtersOf(String storeId, String userId) {
        return orderFiltersRepository.findByOwner(storeId, userId)
                .map(OwnedOrderFilters::getFilters)
                .orElseGet(List::of);
    }
}
