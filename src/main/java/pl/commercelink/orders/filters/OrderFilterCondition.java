package pl.commercelink.orders.filters;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBDocument;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConvertedEnum;

import java.util.LinkedList;
import java.util.List;

@DynamoDBDocument
public class OrderFilterCondition {

    @DynamoDBAttribute(attributeName = "field")
    @DynamoDBTypeConvertedEnum
    private OrderFilterField field;

    @DynamoDBAttribute(attributeName = "values")
    private List<String> values = new LinkedList<>();

    public OrderFilterCondition() {
    }

    public OrderFilterCondition(OrderFilterField field, List<String> values) {
        this.field = field;
        this.values = new LinkedList<>(values);
    }

    public OrderFilterField getField() {
        return field;
    }

    public void setField(OrderFilterField field) {
        this.field = field;
    }

    public List<String> getValues() {
        return values;
    }

    public void setValues(List<String> values) {
        this.values = values;
    }
}
