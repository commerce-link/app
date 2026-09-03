package pl.commercelink.orders.filters.handlers;

import org.springframework.stereotype.Component;
import pl.commercelink.orders.filters.FilterActor;
import pl.commercelink.orders.filters.OrderFilterWriteAccess;
import pl.commercelink.orders.filters.OrderFiltersRepository;
import pl.commercelink.orders.filters.model.OwnedOrderFilters;

@Component
public class DeleteOrderFilterHandler {

    private final OrderFiltersRepository orderFiltersRepository;
    private final OrderFilterWriteAccess writeAccess;

    public DeleteOrderFilterHandler(OrderFiltersRepository orderFiltersRepository, OrderFilterWriteAccess writeAccess) {
        this.orderFiltersRepository = orderFiltersRepository;
        this.writeAccess = writeAccess;
    }

    public void handle(FilterActor actor, String filterId) {
        OwnedOrderFilters ownersFilters = writeAccess.checkWritePermissionsAndReturnByFilterId(actor, filterId);
        if (ownersFilters.remove(filterId)) {
            orderFiltersRepository.save(ownersFilters);
        }
    }
}
