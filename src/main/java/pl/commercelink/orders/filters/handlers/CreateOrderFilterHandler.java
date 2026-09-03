package pl.commercelink.orders.filters.handlers;

import org.springframework.stereotype.Component;
import pl.commercelink.orders.filters.FilterActor;
import pl.commercelink.orders.filters.OrderFilterCondition;
import pl.commercelink.orders.filters.OrderFilterConditions;
import pl.commercelink.orders.filters.OrderFiltersRepository;
import pl.commercelink.orders.filters.model.OrderFilter;
import pl.commercelink.orders.filters.model.OwnedOrderFilters;

import java.util.List;

@Component
public class CreateOrderFilterHandler {

    private final OrderFiltersRepository orderFiltersRepository;
    private final OrderFilterOwnerAccess ownerAccess;

    public CreateOrderFilterHandler(OrderFiltersRepository orderFiltersRepository, OrderFilterOwnerAccess ownerAccess) {
        this.orderFiltersRepository = orderFiltersRepository;
        this.ownerAccess = ownerAccess;
    }

    public OrderFilter handle(FilterActor actor, boolean sharedWithStore, String label,
                              List<OrderFilterCondition> conditions) {
        OwnedOrderFilters owned = ownerAccess.open(actor, sharedWithStore);

        OrderFilter filter = OrderFilter.of(label, OrderFilterConditions.of(conditions));
        owned.add(filter);

        orderFiltersRepository.save(owned);
        return filter;
    }
}
