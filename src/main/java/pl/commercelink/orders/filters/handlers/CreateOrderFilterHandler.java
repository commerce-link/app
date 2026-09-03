package pl.commercelink.orders.filters.handlers;

import org.springframework.stereotype.Component;
import pl.commercelink.orders.filters.FilterActor;
import pl.commercelink.orders.filters.OrderFilterCondition;
import pl.commercelink.orders.filters.OrderFilterConditions;
import pl.commercelink.orders.filters.OrderFilterWriteAccess;
import pl.commercelink.orders.filters.OrderFiltersRepository;
import pl.commercelink.orders.filters.model.OrderFilter;
import pl.commercelink.orders.filters.model.OwnedOrderFilters;

import java.util.List;

@Component
public class CreateOrderFilterHandler {

    private final OrderFiltersRepository orderFiltersRepository;
    private final OrderFilterWriteAccess writeAccess;

    public CreateOrderFilterHandler(OrderFiltersRepository orderFiltersRepository, OrderFilterWriteAccess writeAccess) {
        this.orderFiltersRepository = orderFiltersRepository;
        this.writeAccess = writeAccess;
    }

    public OrderFilter handle(FilterActor actor, boolean sharedWithStore, String label,
                              List<OrderFilterCondition> conditions) {
        OwnedOrderFilters ownersFilters = writeAccess.checkWritePermissionsAndReturn(actor, sharedWithStore);

        OrderFilter newFilter = OrderFilter.of(label, OrderFilterConditions.of(conditions));
        ownersFilters.add(newFilter);

        orderFiltersRepository.save(ownersFilters);
        return newFilter;
    }
}
