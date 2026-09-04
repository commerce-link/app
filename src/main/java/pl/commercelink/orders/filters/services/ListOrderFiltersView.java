package pl.commercelink.orders.filters.services;

import pl.commercelink.orders.filters.model.OrderFilter;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public record ListOrderFiltersView(List<OrderFilter> sharedWithStore, List<OrderFilter> own) {

    public Optional<OrderFilter> byId(String filterId) {
        return filterId == null || filterId.isBlank()
                ? Optional.empty()
                : Stream.concat(sharedWithStore.stream(), own.stream())
                        .filter(filter -> filter.getId().equals(filterId))
                        .findFirst();
    }
}
