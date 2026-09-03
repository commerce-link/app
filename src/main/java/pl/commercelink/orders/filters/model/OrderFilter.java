package pl.commercelink.orders.filters.model;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBDocument;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIgnore;
import pl.commercelink.orders.filters.OrderFilterConditions;
import pl.commercelink.orders.filters.exceptions.OrderFilterInvalidException;

import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

@DynamoDBDocument
public class OrderFilter {

    @DynamoDBAttribute(attributeName = "id")
    private String id;

    @DynamoDBAttribute(attributeName = "label")
    private String label;

    @DynamoDBAttribute(attributeName = "conditions")
    private List<String> conditions = new LinkedList<>();

    public OrderFilter() {
    }

    public static OrderFilter of(String label, OrderFilterConditions conditions) {
        OrderFilter filter = new OrderFilter();
        filter.id = UUID.randomUUID().toString();
        filter.label = validLabel(label);
        filter.conditions = new LinkedList<>(conditions.entries());
        return filter;
    }

    @DynamoDBIgnore
    public void changeTo(String label, OrderFilterConditions conditions) {
        this.label = validLabel(label);
        this.conditions = new LinkedList<>(conditions.entries());
    }

    @DynamoDBIgnore
    public OrderFilterConditions conditions() {
        return OrderFilterConditions.stored(conditions);
    }

    private static String validLabel(String label) {
        if (label == null || label.isBlank()) {
            throw new OrderFilterInvalidException("A filter needs a label");
        }
        return label.trim();
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

    public List<String> getConditions() {
        return conditions;
    }

    public void setConditions(List<String> conditions) {
        this.conditions = conditions;
    }
}
