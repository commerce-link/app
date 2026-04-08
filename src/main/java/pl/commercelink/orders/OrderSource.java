package pl.commercelink.orders;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBDocument;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConvertedEnum;

@DynamoDBDocument
public class OrderSource {
    @DynamoDBAttribute(attributeName = "name")
    private String name;
    @DynamoDBAttribute(attributeName = "type")
    @DynamoDBTypeConvertedEnum
    private OrderSourceType type;

    // required by dynamodb
    public OrderSource() {
    }

    public OrderSource(String name, OrderSourceType type) {
        this.name = name;
        this.type = type;
    }

    // required by dynamodb
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public OrderSourceType getType() {
        return type;
    }

    public void setType(OrderSourceType type) {
        this.type = type;
    }
}
