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

@Service
@RequiredArgsConstructor
public class OrderFiltersService {

    private final OrderFiltersRepository orderFiltersRepository;
    private final OptimisticLockingExecutor optimisticLockingExecutor;

    public ListOrderFiltersView list(FilterActor actor) {
        return new ListOrderFiltersView(
                filtersOf(actor.storeId(), OwnedOrderFilters.STORE_FILTER),
                filtersOf(actor.storeId(), actor.userId()));
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
                ownersFilters -> ownersFilters.remove(filterId),
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
