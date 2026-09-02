package pl.commercelink.orders.filters.handlers;

import org.springframework.stereotype.Component;
import pl.commercelink.orders.filters.FilterActor;
import pl.commercelink.orders.filters.OrderFiltersRepository;

@Component
public class DeleteOrderFilterHandler {

    private final OrderFiltersRepository orderFiltersRepository;

    public DeleteOrderFilterHandler(OrderFiltersRepository orderFiltersRepository) {
        this.orderFiltersRepository = orderFiltersRepository;
    }

    public void handle(FilterActor actor, String filterKey) {
        orderFiltersRepository.findById(actor.storeId(), filterKey).ifPresent(filter -> {
            filter.requireWritableBy(actor);
            orderFiltersRepository.delete(filter);
        });
    }
}
