package pl.commercelink.orders.filters.services;

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
public class CreateOrderFilterCommandService {

    private final OrderFiltersRepository orderFiltersRepository;
    private final OrderFilterWriteAccess writeAccess;

    public CreateOrderFilterCommandService(OrderFiltersRepository orderFiltersRepository, OrderFilterWriteAccess writeAccess) {
        this.orderFiltersRepository = orderFiltersRepository;
        this.writeAccess = writeAccess;
    }

    public OrderFilter create(FilterActor actor, boolean sharedWithStore, String label,
                              List<OrderFilterCondition> conditions) {
        OwnedOrderFilters ownersFilters = writeAccess.checkWritePermissionsAndReturn(actor, sharedWithStore);

        OrderFilter newFilter = OrderFilter.of(label, OrderFilterConditions.of(conditions));
        ownersFilters.add(newFilter);

        orderFiltersRepository.save(ownersFilters);
        return newFilter;
    }
}
