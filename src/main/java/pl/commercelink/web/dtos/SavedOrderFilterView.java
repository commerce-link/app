package pl.commercelink.web.dtos;

import pl.commercelink.orders.filters.OrderFilter;
import pl.commercelink.orders.filters.OrderFilterCondition;

import java.util.LinkedHashMap;
import java.util.Map;

public record SavedOrderFilterView(String filterKey, String label, boolean sharedWithStore,
                                   Map<String, String> conditionsByField) {

    public static SavedOrderFilterView of(OrderFilter filter) {
        Map<String, String> byField = new LinkedHashMap<>();
        for (OrderFilterCondition condition : filter.conditions().conditions()) {
            byField.put(condition.field().name(), condition.value());
        }
        return new SavedOrderFilterView(filter.getFilterKey(), filter.getLabel(), filter.isSharedWithStore(), byField);
    }
}
