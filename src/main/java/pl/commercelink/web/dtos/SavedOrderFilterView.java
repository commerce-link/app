package pl.commercelink.web.dtos;

import pl.commercelink.orders.filters.OrderFilterCondition;
import pl.commercelink.orders.filters.model.OrderFilter;
import pl.commercelink.orders.filters.model.OrderFilterConditionSerializer;

import java.util.LinkedHashMap;
import java.util.Map;

public record SavedOrderFilterView(String id, String label, boolean sharedWithStore,
                                   Map<String, String> conditionsByField) {

    public static SavedOrderFilterView of(OrderFilter filter, boolean sharedWithStore) {
        Map<String, String> byField = new LinkedHashMap<>();
        OrderFilterConditionSerializer.fromStoredEntries(filter.getConditions())
                .ifPresent(conditions -> conditions.conditions()
                        .forEach(condition -> byField.put(condition.field().name(), condition.value())));
        return new SavedOrderFilterView(filter.getId(), filter.getLabel(), sharedWithStore, byField);
    }
}
