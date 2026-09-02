package pl.commercelink.orders.filters;

import pl.commercelink.orders.filters.model.OrderFilter;

import java.util.List;
import java.util.Optional;

public record VisibleOrderFilters(List<OrderFilter> all) {

    public Optional<OrderFilter> byKey(String filterKey) {
        return filterKey == null || filterKey.isBlank()
                ? Optional.empty()
                : all.stream().filter(filter -> filter.getFilterKey().equals(filterKey)).findFirst();
    }
}
