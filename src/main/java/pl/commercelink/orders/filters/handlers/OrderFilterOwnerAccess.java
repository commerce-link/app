package pl.commercelink.orders.filters.handlers;

import org.springframework.stereotype.Component;
import pl.commercelink.orders.filters.FilterActor;
import pl.commercelink.orders.filters.OrderFilterAccessDeniedException;
import pl.commercelink.orders.filters.OrderFilterInvalidException;
import pl.commercelink.orders.filters.OrderFiltersRepository;
import pl.commercelink.orders.filters.model.OwnedOrderFilters;

@Component
public class OrderFilterOwnerAccess {

    private final OrderFiltersRepository orderFiltersRepository;

    public OrderFilterOwnerAccess(OrderFiltersRepository orderFiltersRepository) {
        this.orderFiltersRepository = orderFiltersRepository;
    }

    public OwnedOrderFilters open(FilterActor actor, boolean sharedWithStore) {
        if (sharedWithStore && !actor.administrator()) {
            throw new OrderFilterAccessDeniedException("Only an administrator can change the filters shared with the store");
        }
        String userId = sharedWithStore ? OwnedOrderFilters.WHOLE_STORE : actor.userId();
        return orderFiltersRepository.findByOwner(actor.storeId(), userId)
                .orElseGet(() -> OwnedOrderFilters.emptyFor(actor.storeId(), userId));
    }

    public OwnedOrderFilters openContaining(FilterActor actor, String filterId) {
        OwnedOrderFilters wholeStore = orderFiltersRepository
                .findByOwner(actor.storeId(), OwnedOrderFilters.WHOLE_STORE)
                .filter(owned -> owned.byId(filterId).isPresent())
                .orElse(null);

        if (wholeStore != null) {
            if (!actor.administrator()) {
                throw new OrderFilterAccessDeniedException(
                        "Only an administrator can change the filters shared with the store");
            }
            return wholeStore;
        }

        return orderFiltersRepository.findByOwner(actor.storeId(), actor.userId())
                .filter(owned -> owned.byId(filterId).isPresent())
                .orElseThrow(() -> new OrderFilterInvalidException("The filter no longer exists"));
    }
}
