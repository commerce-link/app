package pl.commercelink.orders.filters.model;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBDocument;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIgnore;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.filters.exceptions.OrderFilterInvalidException;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@DynamoDBDocument
public class OrderFilter {

    @DynamoDBAttribute(attributeName = "id")
    private String id;

    @DynamoDBAttribute(attributeName = "label")
    private String label;

    @DynamoDBAttribute(attributeName = "conditions")
    private List<OrderFilterCondition> conditions = new LinkedList<>();

    public OrderFilter() {
    }

    public static OrderFilter of(String label, List<OrderFilterCondition> conditions) {
        OrderFilter filter = new OrderFilter();
        filter.id = UUID.randomUUID().toString();
        filter.label = validLabel(label);
        filter.conditions = validConditions(conditions);
        return filter;
    }

    public static void checkValid(String label, List<OrderFilterCondition> conditions) {
        validLabel(label);
        validConditions(conditions);
    }

    public void changeTo(String label, List<OrderFilterCondition> conditions) {
        this.label = validLabel(label);
        this.conditions = validConditions(conditions);
    }

    public boolean matches(Order order, LocalDate today) {
        return conditions != null
                && !conditions.isEmpty()
                && conditions.stream().allMatch(condition -> condition.matches(order, today));
    }

    private static String validLabel(String label) {
        if (label == null || label.isBlank()) {
            throw new OrderFilterInvalidException("orders.filters.error.no.label");
        }
        return label.trim();
    }

    private static List<OrderFilterCondition> validConditions(List<OrderFilterCondition> conditions) {
        List<OrderFilterCondition> unique = conditions == null ? List.of() : conditions.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (unique.isEmpty()) {
            throw new OrderFilterInvalidException("orders.filters.error.no.conditions");
        }
        return new LinkedList<>(unique);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public List<OrderFilterCondition> getConditions() {
        return conditions;
    }

    public void setConditions(List<OrderFilterCondition> conditions) {
        this.conditions = conditions;
    }

    /** The filter's conditions as field.name() -> value, for the "Edytuj" button to prefill the form (spec §7.4). */
    @DynamoDBIgnore
    public Map<String, String> getConditionsByField() {
        Map<String, String> byField = new LinkedHashMap<>();
        conditions.stream()
                .filter(condition -> Objects.nonNull(condition.getField()))
                .forEach(condition -> byField.put(condition.getField().name(), condition.getValue()));
        return byField;
    }
}
