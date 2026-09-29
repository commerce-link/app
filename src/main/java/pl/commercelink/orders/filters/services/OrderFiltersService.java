package pl.commercelink.orders.filters.services;

import com.amazonaws.services.dynamodbv2.model.TransactionCanceledException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.commercelink.orders.filters.FilterActor;
import pl.commercelink.orders.filters.OrderFiltersRepository;
import pl.commercelink.orders.filters.exceptions.OrderFilterAccessDeniedException;
import pl.commercelink.orders.filters.exceptions.OrderFilterConflictException;
import pl.commercelink.orders.filters.exceptions.OrderFilterInvalidException;
import pl.commercelink.orders.filters.model.OrderFilter;
import pl.commercelink.orders.filters.model.OrderFilterCondition;
import pl.commercelink.orders.filters.model.OwnedOrderFilters;
import pl.commercelink.starter.dynamodb.OptimisticLockingExecutor;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class OrderFiltersService {

    private final OrderFiltersRepository orderFiltersRepository;
    private final OptimisticLockingExecutor optimisticLockingExecutor;

    public ListOrderFiltersView list(FilterActor actor) {
        Optional<OwnedOrderFilters> own = orderFiltersRepository.findByOwner(actor.storeId(), actor.userId());
        return new ListOrderFiltersView(
                filtersOf(actor.storeId(), OwnedOrderFilters.STORE_FILTER),
                own.map(OwnedOrderFilters::getFilters).orElseGet(List::of),
                own.map(OwnedOrderFilters::getDefaultFilterId).orElse(null));
    }

    /** Makes the actor's orders list open with this filter; any filter they can see, their own or the store's, will do. */
    public void setDefault(FilterActor actor, String filterId) {
        if (list(actor).byId(filterId).isEmpty()) {
            throw new OrderFilterInvalidException("orders.filters.error.not.found");
        }
        optimisticLockingExecutor.modifyAndSave(
                () -> ownRow(actor),
                own -> own.setDefaultFilterId(filterId),
                orderFiltersRepository::save);
    }

    /**
     * Stops the actor's orders list opening with this filter. Only this one: a page left open from before the user
     * chose another default must not clear the newer choice.
     */
    public void clearDefault(FilterActor actor, String filterId) {
        boolean isDefault = orderFiltersRepository.findByOwner(actor.storeId(), actor.userId())
                .filter(own -> own.isDefault(filterId))
                .isPresent();
        if (!isDefault) {
            return;
        }
        optimisticLockingExecutor.modifyAndSave(
                () -> ownRow(actor),
                own -> {
                    if (own.isDefault(filterId)) {
                        own.setDefaultFilterId(null);
                    }
                },
                orderFiltersRepository::save);
    }

    public OrderFilter create(FilterActor actor, boolean sharedWithStore, String label,
                              List<OrderFilterCondition> conditions) {
        OrderFilter.checkValid(label, conditions);
        checkWritePermissionsAndReturn(actor, sharedWithStore).checkRoomForOneMore();

        return optimisticLockingExecutor.modifyAndSaveReturning(
                () -> checkWritePermissionsAndReturn(actor, sharedWithStore),
                ownersFilters -> {
                    OrderFilter newFilter = OrderFilter.of(label, conditions);
                    ownersFilters.add(newFilter);
                    return newFilter;
                },
                orderFiltersRepository::save);
    }

    public OrderFilter update(FilterActor actor, String filterId, boolean sharedWithStore, String label,
                              List<OrderFilterCondition> conditions) {
        OrderFilter.checkValid(label, conditions);

        if (sharedWithStore != checkWritePermissionsAndReturnByFilterId(actor, filterId).isFiltersForStore()) {
            return moveBetweenScopes(actor, filterId, sharedWithStore, label, conditions);
        }

        return optimisticLockingExecutor.modifyAndSaveReturning(
                () -> checkWritePermissionsAndReturnByFilterId(actor, filterId),
                ownersFilters -> {
                    OrderFilter filterToUpdate = ownersFilters.byId(filterId)
                            .orElseThrow(() -> new OrderFilterInvalidException("orders.filters.error.not.found"));
                    filterToUpdate.changeTo(label, conditions);
                    return filterToUpdate;
                },
                orderFiltersRepository::save);
    }

    public void delete(FilterActor actor, String filterId) {
        checkWritePermissionsAndReturnByFilterId(actor, filterId);

        optimisticLockingExecutor.modifyAndSave(
                () -> checkWritePermissionsAndReturnByFilterId(actor, filterId),
                ownersFilters -> {
                    ownersFilters.remove(filterId);
                    // the user's own filter was their default: it goes with it. A deleted store filter that other
                    // users chose stays named in their rows and reads as no default (ListOrderFiltersView).
                    if (ownersFilters.isDefault(filterId)) {
                        ownersFilters.setDefaultFilterId(null);
                    }
                },
                orderFiltersRepository::save);
    }

    private OrderFilter moveBetweenScopes(FilterActor actor, String filterId, boolean sharedWithStore, String label,
                                          List<OrderFilterCondition> conditions) {
        OwnedOrderFilters currentOwnersFilters = checkWritePermissionsAndReturnByFilterId(actor, filterId);
        OrderFilter filterToUpdate = currentOwnersFilters.byId(filterId)
                .orElseThrow(() -> new OrderFilterInvalidException("orders.filters.error.not.found"));

        filterToUpdate.changeTo(label, conditions);

        OwnedOrderFilters newOwnersFilters = checkWritePermissionsAndReturn(actor, sharedWithStore);
        currentOwnersFilters.remove(filterId);
        newOwnersFilters.add(filterToUpdate);

        try {
            orderFiltersRepository.saveBoth(newOwnersFilters, currentOwnersFilters);
        } catch (TransactionCanceledException e) {
            throw new OrderFilterConflictException("orders.filters.error.conflict");
        }
        return filterToUpdate;
    }

    private List<OrderFilter> filtersOf(String storeId, String userId) {
        return orderFiltersRepository.findByOwner(storeId, userId)
                .map(OwnedOrderFilters::getFilters)
                .orElseGet(List::of);
    }

    private OwnedOrderFilters ownRow(FilterActor actor) {
        return orderFiltersRepository.findByOwner(actor.storeId(), actor.userId())
                .orElseGet(() -> OwnedOrderFilters.emptyFor(actor.storeId(), actor.userId()));
    }

    private OwnedOrderFilters checkWritePermissionsAndReturn(FilterActor actor, boolean sharedWithStore) {
        if (sharedWithStore && !actor.administrator()) {
            throw new OrderFilterAccessDeniedException("orders.filters.error.store.only.admin");
        }
        String owner = sharedWithStore ? OwnedOrderFilters.STORE_FILTER : actor.userId();
        return orderFiltersRepository.findByOwner(actor.storeId(), owner)
                .orElseGet(() -> OwnedOrderFilters.emptyFor(actor.storeId(), owner));
    }

    private OwnedOrderFilters checkWritePermissionsAndReturnByFilterId(FilterActor actor, String filterId) {
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
