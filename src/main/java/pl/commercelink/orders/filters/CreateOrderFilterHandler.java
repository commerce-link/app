package pl.commercelink.orders.filters;

import org.springframework.stereotype.Component;

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
