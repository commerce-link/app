package pl.commercelink.products;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBDocument;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIgnore;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConvertedEnum;
import pl.commercelink.starter.dynamodb.Metadata;
import pl.commercelink.inventory.MatchedInventory;
import pl.commercelink.products.filters.InventoryFilterType;

import java.util.LinkedList;
import java.util.List;
import java.util.function.Predicate;

@DynamoDBDocument
public class InventoryDefinition implements Predicate<MatchedInventory> {

    public InventoryDefinition() {
    }

    public InventoryDefinition(InventoryFilterType type, List<Metadata> metadata) {
        this.type = type;
        this.metadata = metadata;
    }

    @DynamoDBAttribute(attributeName = "type")
    @DynamoDBTypeConvertedEnum
    private InventoryFilterType type;

    @DynamoDBAttribute(attributeName = "metadata")
    private List<Metadata> metadata = new LinkedList<>();

    @Override
    public boolean test(MatchedInventory matchedInventory) {
        try {
            return type.getInstance().run(matchedInventory, metadata);
        } catch (Exception e) {
            return false;
        }
    }

    // required by DynamoDB
    public InventoryFilterType getType() {
        return type;
    }

    public void setType(InventoryFilterType type) {
        this.type = type;
    }

    public List<Metadata> getMetadata() {
        return metadata;
    }

    public void setMetadata(List<Metadata> metadata) {
        this.metadata = metadata;
    }

    @DynamoDBIgnore
    public boolean isComplete() {
        if (type == null || metadata == null || metadata.isEmpty()) {
            return false;
        }

        try {
            return type.getInstance().canRun(metadata);
        } catch (Exception e) {
            return false;
        }
    }
}
