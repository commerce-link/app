package pl.commercelink.orders.filters.handlers;

import org.springframework.stereotype.Component;
import pl.commercelink.orders.filters.FilterActor;
import pl.commercelink.orders.filters.OrderFiltersRepository;
import pl.commercelink.orders.filters.model.OwnedOrderFilters;

@Component
public class DeleteOrderFilterHandler {

    private final OrderFiltersRepository orderFiltersRepository;
    private final OrderFilterOwnerAccess ownerAccess;

    public DeleteOrderFilterHandler(OrderFiltersRepository orderFiltersRepository, OrderFilterOwnerAccess ownerAccess) {
        this.orderFiltersRepository = orderFiltersRepository;
        this.ownerAccess = ownerAccess;
    }

    public void handle(FilterActor actor, String filterId) {
        OwnedOrderFilters owned = ownerAccess.openContaining(actor, filterId);
        if (owned.remove(filterId)) {
            orderFiltersRepository.save(owned);
        }
    }
}
