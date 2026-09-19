package pl.commercelink.stores;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBDocument;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIgnore;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConvertedEnum;
import pl.commercelink.orders.ShipmentType;
import pl.commercelink.starter.util.UniqueIdentifierGenerator;

@DynamoDBDocument
public class DeliveryOption {

    @DynamoDBAttribute(attributeName = "id")
    private String id;

    @DynamoDBAttribute(attributeName = "name")
    private String name;

    @DynamoDBAttribute(attributeName = "description")
    private String description;

    @DynamoDBAttribute(attributeName = "price")
    private double price;

    @DynamoDBAttribute(attributeName = "type")
    @DynamoDBTypeConvertedEnum
    private ShipmentType type = ShipmentType.Courier;

    // A removed option stays in the store so offers and baskets that chose it keep resolving it: the payment webhook
    // and the offer page look the option up by id and failed once it was gone, so a paid basket produced no order.
    @DynamoDBAttribute(attributeName = "retired")
    private boolean retired;

    public DeliveryOption() {
        this.id = UniqueIdentifierGenerator.generate();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public ShipmentType getType() {
        return type;
    }

    public void setType(ShipmentType type) {
        this.type = type;
    }

    public boolean isRetired() {
        return retired;
    }

    public void setRetired(boolean retired) {
        this.retired = retired;
    }

    @DynamoDBIgnore
    public boolean isComplete() {
        return name != null && !name.isBlank();
    }
}
