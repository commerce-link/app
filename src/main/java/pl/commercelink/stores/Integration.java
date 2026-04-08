package pl.commercelink.stores;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConvertedEnum;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBDocument;
@DynamoDBDocument
public class Integration {

    @DynamoDBAttribute(attributeName = "type")
    @DynamoDBTypeConvertedEnum
    private IntegrationType type;
    @DynamoDBAttribute(attributeName = "name")
    private String name;

    public Integration() {
    }

    public Integration(IntegrationType type, String name) {
        this.type = type;
        this.name = name;
    }

    public IntegrationType getType() {
        return type;
    }

    public void setType(IntegrationType type) {
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
