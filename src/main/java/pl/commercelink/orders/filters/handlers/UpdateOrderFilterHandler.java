package pl.commercelink.orders.filters.handlers;

import org.springframework.stereotype.Component;
import pl.commercelink.orders.filters.FilterActor;
import pl.commercelink.orders.filters.model.OrderFilter;
import pl.commercelink.orders.filters.OrderFilterCondition;
import pl.commercelink.orders.filters.OrderFilterConditions;
import pl.commercelink.orders.filters.OrderFilterInvalidException;
import pl.commercelink.orders.filters.OrderFiltersRepository;

import java.util.List;

@Component
public class UpdateOrderFilterHandler {

    private final OrderFiltersRepository orderFiltersRepository;

    public UpdateOrderFilterHandler(OrderFiltersRepository orderFiltersRepository) {
        this.orderFiltersRepository = orderFiltersRepository;
    }

    public OrderFilter handle(FilterActor actor, String filterKey, String label,
                              List<OrderFilterCondition> conditions) {
        OrderFilter existing = orderFiltersRepository.findById(actor.storeId(), filterKey)
                .orElseThrow(() -> new OrderFilterInvalidException("The filter no longer exists"));
        existing.requireWritableBy(actor);

        OrderFilter replacement = existing.withConditions(label, OrderFilterConditions.of(conditions));

        if (replacement.getFilterKey().equals(existing.getFilterKey())) {
            existing.setLabel(replacement.getLabel());
            orderFiltersRepository.save(existing);
            return existing;
        }

        orderFiltersRepository.findById(actor.storeId(), replacement.getFilterKey()).ifPresent(clash -> {
            throw new OrderFilterInvalidException("The same filter already exists under the label " + clash.getLabel());
        });

        orderFiltersRepository.save(replacement);
        orderFiltersRepository.delete(existing);
        return replacement;
    }
}
