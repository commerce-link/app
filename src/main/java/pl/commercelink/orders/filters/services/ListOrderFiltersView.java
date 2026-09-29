package pl.commercelink.orders.filters.services;

import pl.commercelink.orders.filters.model.OrderFilter;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * The filters a user sees, and the one their orders list opens with. {@code defaultFilterId} is set only while it names
 * a filter in this view: a default whose filter was deleted, or made private by an administrator, reads as none.
 */
public record ListOrderFiltersView(List<OrderFilter> sharedWithStore, List<OrderFilter> own, String defaultFilterId) {

    public ListOrderFiltersView(List<OrderFilter> sharedWithStore, List<OrderFilter> own) {
        this(sharedWithStore, own, null);
    }

    public ListOrderFiltersView {
        if (!names(sharedWithStore, own, defaultFilterId)) {
            defaultFilterId = null;
        }
    }

    private static boolean names(List<OrderFilter> sharedWithStore, List<OrderFilter> own, String filterId) {
        return filterId != null && Stream.concat(sharedWithStore.stream(), own.stream())
                .anyMatch(filter -> filter.getId().equals(filterId));
    }

    public Optional<OrderFilter> byId(String filterId) {
        return filterId == null || filterId.isBlank()
                ? Optional.empty()
                : Stream.concat(sharedWithStore.stream(), own.stream())
                        .filter(filter -> filter.getId().equals(filterId))
                        .findFirst();
    }

    public Optional<OrderFilter> defaultFilter() {
        return byId(defaultFilterId);
    }

    public boolean isDefault(String filterId) {
        return filterId != null && filterId.equals(defaultFilterId);
    }
}
