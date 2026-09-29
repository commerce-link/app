package pl.commercelink.orders.filters.model;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBDocument;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIgnore;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.filters.OrderFilterField;
import pl.commercelink.orders.filters.exceptions.OrderFilterInvalidException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

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
                && matchesIgnoring(null, order, today);
    }

    /**
     * Values of one field are alternatives, different fields all have to hold: "(Allegro or Ceneo) and Courier".
     * The orders list passes Status here, because once a filter is chosen the Status menu decides the status.
     */
    public boolean matchesIgnoring(OrderFilterField ignored, Order order, LocalDate today) {
        List<OrderFilterCondition> all = conditions == null ? List.of() : conditions;
        // a condition whose field did not survive storage matched nothing before; it keeps failing the filter
        if (all.stream().anyMatch(condition -> condition.getField() == null)) {
            return false;
        }
        return all.stream()
                .filter(condition -> condition.getField() != ignored)
                .collect(Collectors.groupingBy(OrderFilterCondition::getField, LinkedHashMap::new, Collectors.toList()))
                .values().stream()
                .allMatch(alternatives -> alternatives.stream().anyMatch(condition -> condition.matches(order, today)));
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

    /** The filter's values per field (field.name() -> values in saved order), for the edit form and the condition pills. */
    @DynamoDBIgnore
    public Map<String, List<String>> getConditionsByField() {
        Map<String, List<String>> byField = new LinkedHashMap<>();
        conditions.stream()
                .filter(condition -> Objects.nonNull(condition.getField()))
                .forEach(condition -> byField.computeIfAbsent(condition.getField().name(), field -> new ArrayList<>())
                        .add(condition.getValue()));
        return byField;
    }
}
