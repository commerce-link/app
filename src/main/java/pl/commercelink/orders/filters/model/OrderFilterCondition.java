package pl.commercelink.orders.filters.model;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBDocument;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConvertedEnum;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.filters.OrderFilterField;

import java.time.LocalDate;
import java.util.Objects;

@DynamoDBDocument
public class OrderFilterCondition {

    @DynamoDBAttribute(attributeName = "field")
    @DynamoDBTypeConvertedEnum
    private OrderFilterField field;

    @DynamoDBAttribute(attributeName = "value")
    private String value;

    public OrderFilterCondition() {
    }

    public static OrderFilterCondition of(OrderFilterField field, String rawValue) {
        OrderFilterCondition condition = new OrderFilterCondition();
        condition.field = field;
        condition.value = field.normalize(rawValue);
        return condition;
    }

    public boolean matches(Order order, LocalDate today) {
        return field != null && field.matches(order, value, today);
    }

    public OrderFilterField getField() {
        return field;
    }

    public void setField(OrderFilterField field) {
        this.field = field;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof OrderFilterCondition that
                && field == that.field
                && Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(field, value);
    }

    @Override
    public String toString() {
        return field + "=" + value;
    }
}
