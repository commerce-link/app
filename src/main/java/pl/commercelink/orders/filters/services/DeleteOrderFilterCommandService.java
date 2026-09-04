package pl.commercelink.orders.filters.services;

import org.springframework.stereotype.Component;
import pl.commercelink.orders.filters.FilterActor;
import pl.commercelink.orders.filters.OrderFilterWriteAccess;
import pl.commercelink.orders.filters.OrderFiltersRepository;
import pl.commercelink.orders.filters.model.OwnedOrderFilters;

@Component
public class DeleteOrderFilterCommandService {

    private final OrderFiltersRepository orderFiltersRepository;
    private final OrderFilterWriteAccess writeAccess;

    public DeleteOrderFilterCommandService(OrderFiltersRepository orderFiltersRepository, OrderFilterWriteAccess writeAccess) {
        this.orderFiltersRepository = orderFiltersRepository;
        this.writeAccess = writeAccess;
    }

    public void delete(FilterActor actor, String filterId) {
        OwnedOrderFilters ownersFilters = writeAccess.checkWritePermissionsAndReturnByFilterId(actor, filterId);
        if (ownersFilters.remove(filterId)) {
            orderFiltersRepository.save(ownersFilters);
        }
    }
}
