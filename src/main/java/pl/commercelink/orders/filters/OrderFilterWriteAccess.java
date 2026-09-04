package pl.commercelink.orders.filters;

import pl.commercelink.orders.filters.exceptions.OrderFilterInvalidException;
import pl.commercelink.orders.filters.exceptions.OrderFilterAccessDeniedException;
import org.springframework.stereotype.Component;
import pl.commercelink.orders.filters.model.OwnedOrderFilters;

@Component
public class OrderFilterWriteAccess {

    private final OrderFiltersRepository orderFiltersRepository;

    public OrderFilterWriteAccess(OrderFiltersRepository orderFiltersRepository) {
        this.orderFiltersRepository = orderFiltersRepository;
    }

    public OwnedOrderFilters checkWritePermissionsAndReturn(FilterActor actor, boolean sharedWithStore) {
        if (sharedWithStore && !actor.administrator()) {
            throw new OrderFilterAccessDeniedException("orders.filters.error.store.only.admin");
        }
        String owner = sharedWithStore ? OwnedOrderFilters.STORE_FILTER : actor.userId();
        return orderFiltersRepository.findByOwner(actor.storeId(), owner)
                .orElseGet(() -> OwnedOrderFilters.emptyFor(actor.storeId(), owner));
    }

    public OwnedOrderFilters checkWritePermissionsAndReturnByFilterId(FilterActor actor, String filterId) {
        boolean sharedWithStore = orderFiltersRepository
                .findByOwner(actor.storeId(), OwnedOrderFilters.STORE_FILTER)
                .filter(filters -> filters.byId(filterId).isPresent())
                .isPresent();

        if (sharedWithStore) {
            return checkWritePermissionsAndReturn(actor, true);
        }

        return orderFiltersRepository.findByOwner(actor.storeId(), actor.userId())
                .filter(filters -> filters.byId(filterId).isPresent())
                .orElseThrow(() -> new OrderFilterInvalidException("orders.filters.error.not.found"));
    }
}
