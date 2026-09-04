package pl.commercelink.orders.filters.services;

import org.springframework.stereotype.Component;
import pl.commercelink.orders.filters.FilterActor;
import pl.commercelink.orders.filters.OrderFiltersRepository;
import pl.commercelink.orders.filters.model.OrderFilter;
import pl.commercelink.orders.filters.model.OwnedOrderFilters;

import java.util.List;

@Component
public class ListOrderFiltersQueryService {

    private final OrderFiltersRepository orderFiltersRepository;

    public ListOrderFiltersQueryService(OrderFiltersRepository orderFiltersRepository) {
        this.orderFiltersRepository = orderFiltersRepository;
    }

    public ListOrderFiltersView list(FilterActor actor) {
        return new ListOrderFiltersView(
                filtersOf(actor.storeId(), OwnedOrderFilters.STORE_FILTER),
                filtersOf(actor.storeId(), actor.userId()));
    }

    private List<OrderFilter> filtersOf(String storeId, String userId) {
        return orderFiltersRepository.findByOwner(storeId, userId)
                .map(OwnedOrderFilters::getFilters)
                .orElseGet(List::of);
    }
}
