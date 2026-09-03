package pl.commercelink.orders.filters.handlers;

import org.springframework.stereotype.Component;
import pl.commercelink.orders.filters.FilterActor;
import pl.commercelink.orders.filters.OrderFilterCondition;
import pl.commercelink.orders.filters.OrderFilterConditions;
import pl.commercelink.orders.filters.OrderFilterInvalidException;
import pl.commercelink.orders.filters.OrderFiltersRepository;
import pl.commercelink.orders.filters.model.OrderFilter;
import pl.commercelink.orders.filters.model.OwnedOrderFilters;

import java.util.List;

@Component
public class UpdateOrderFilterHandler {

    private final OrderFiltersRepository orderFiltersRepository;
    private final OrderFilterOwnerAccess ownerAccess;

    public UpdateOrderFilterHandler(OrderFiltersRepository orderFiltersRepository, OrderFilterOwnerAccess ownerAccess) {
        this.orderFiltersRepository = orderFiltersRepository;
        this.ownerAccess = ownerAccess;
    }

    public OrderFilter handle(FilterActor actor, String filterId, boolean sharedWithStore, String label,
                              List<OrderFilterCondition> conditions) {
        OwnedOrderFilters current = ownerAccess.openContaining(actor, filterId);
        OrderFilter filter = current.byId(filterId)
                .orElseThrow(() -> new OrderFilterInvalidException("The filter no longer exists"));

        filter.changeTo(label, OrderFilterConditions.of(conditions));

        if (sharedWithStore == current.isWholeStore()) {
            orderFiltersRepository.save(current);
            return filter;
        }

        OwnedOrderFilters target = ownerAccess.open(actor, sharedWithStore);
        current.remove(filterId);
        target.add(filter);
        orderFiltersRepository.save(target);
        orderFiltersRepository.save(current);
        return filter;
    }
}
