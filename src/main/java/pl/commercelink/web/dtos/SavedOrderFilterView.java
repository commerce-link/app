package pl.commercelink.web.dtos;

import pl.commercelink.orders.filters.model.OrderFilter;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record SavedOrderFilterView(String id, String label, boolean sharedWithStore,
                                   Map<String, String> conditionsByField) {

    public static SavedOrderFilterView of(OrderFilter filter, boolean sharedWithStore) {
        Map<String, String> byField = new LinkedHashMap<>();
        filter.getConditions().stream()
                .filter(condition -> Objects.nonNull(condition.getField()))
                .forEach(condition -> byField.put(condition.getField().name(), condition.getValue()));
        return new SavedOrderFilterView(filter.getId(), filter.getLabel(), sharedWithStore, byField);
    }
}
