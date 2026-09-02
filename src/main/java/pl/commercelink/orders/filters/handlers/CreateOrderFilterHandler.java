package pl.commercelink.orders.filters.handlers;

import org.springframework.stereotype.Component;
import pl.commercelink.orders.filters.FilterActor;
import pl.commercelink.orders.filters.model.OrderFilter;
import pl.commercelink.orders.filters.OrderFilterAccessDeniedException;
import pl.commercelink.orders.filters.OrderFilterCondition;
import pl.commercelink.orders.filters.OrderFilterConditions;
import pl.commercelink.orders.filters.OrderFilterInvalidException;
import pl.commercelink.orders.filters.OrderFiltersRepository;

import java.util.List;

@Component
public class CreateOrderFilterHandler {

    private final OrderFiltersRepository orderFiltersRepository;

    public CreateOrderFilterHandler(OrderFiltersRepository orderFiltersRepository) {
        this.orderFiltersRepository = orderFiltersRepository;
    }

    public OrderFilter handle(FilterActor actor, boolean sharedWithStore, String label,
                              List<OrderFilterCondition> conditions) {
        if (sharedWithStore && !actor.administrator()) {
            throw new OrderFilterAccessDeniedException("Only an administrator can create a filter shared with the store");
        }

        OrderFilterConditions filterConditions = OrderFilterConditions.of(conditions);
        OrderFilter filter = sharedWithStore
                ? OrderFilter.sharedWithStore(actor.storeId(), label, filterConditions)
                : OrderFilter.ownedBy(actor.storeId(), actor.userId(), label, filterConditions);

        orderFiltersRepository.findById(actor.storeId(), filter.getFilterKey()).ifPresent(clash -> {
            throw new OrderFilterInvalidException("The same filter already exists under the label " + clash.getLabel());
        });

        orderFiltersRepository.save(filter);
        return filter;
    }
}
