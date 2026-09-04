package pl.commercelink.orders.filters.services;

import org.springframework.stereotype.Component;
import pl.commercelink.orders.filters.FilterActor;
import pl.commercelink.orders.filters.OrderFilterCondition;
import pl.commercelink.orders.filters.OrderFilterConditions;
import pl.commercelink.orders.filters.exceptions.OrderFilterInvalidException;
import pl.commercelink.orders.filters.OrderFilterWriteAccess;
import pl.commercelink.orders.filters.OrderFiltersRepository;
import pl.commercelink.orders.filters.model.OrderFilter;
import pl.commercelink.orders.filters.model.OwnedOrderFilters;

import java.util.List;

@Component
public class UpdateOrderFilterCommandService {

    private final OrderFiltersRepository orderFiltersRepository;
    private final OrderFilterWriteAccess writeAccess;

    public UpdateOrderFilterCommandService(OrderFiltersRepository orderFiltersRepository, OrderFilterWriteAccess writeAccess) {
        this.orderFiltersRepository = orderFiltersRepository;
        this.writeAccess = writeAccess;
    }

    public OrderFilter update(FilterActor actor, String filterId, boolean sharedWithStore, String label,
                              List<OrderFilterCondition> conditions) {
        OwnedOrderFilters currentOwnersFilters = writeAccess.checkWritePermissionsAndReturnByFilterId(actor, filterId);
        OrderFilter filterToUpdate = currentOwnersFilters.byId(filterId)
                .orElseThrow(() -> new OrderFilterInvalidException("orders.filters.error.not.found"));

        filterToUpdate.changeTo(label, OrderFilterConditions.of(conditions));

        if (sharedWithStore == currentOwnersFilters.isFiltersForStore()) {
            orderFiltersRepository.save(currentOwnersFilters);
            return filterToUpdate;
        }

        OwnedOrderFilters newOwnersFilters = writeAccess.checkWritePermissionsAndReturn(actor, sharedWithStore);
        currentOwnersFilters.remove(filterId);
        newOwnersFilters.add(filterToUpdate);

        orderFiltersRepository.save(newOwnersFilters);
        orderFiltersRepository.save(currentOwnersFilters);
        return filterToUpdate;
    }
}
