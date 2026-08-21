package pl.commercelink.migration;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.model.AttributeDefinition;
import com.amazonaws.services.dynamodbv2.model.CreateGlobalSecondaryIndexAction;
import com.amazonaws.services.dynamodbv2.model.GlobalSecondaryIndexUpdate;
import com.amazonaws.services.dynamodbv2.model.KeySchemaElement;
import com.amazonaws.services.dynamodbv2.model.KeyType;
import com.amazonaws.services.dynamodbv2.model.Projection;
import com.amazonaws.services.dynamodbv2.model.ProjectionType;
import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType;
import com.amazonaws.services.dynamodbv2.model.UpdateTableRequest;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;

@ChangeUnit(id = "V010-add-claimed-delivery-id-index", order = "010", author = "commercelink")
public class V010_AddClaimedDeliveryIdIndex {

    private final AmazonDynamoDB dynamoDB;

    public V010_AddClaimedDeliveryIdIndex(AmazonDynamoDB dynamoDB) {
        this.dynamoDB = dynamoDB;
    }

    @Execution
    public void execute() {
        boolean indexExists = dynamoDB.describeTable("OrderItems").getTable()
                .getGlobalSecondaryIndexes() != null
                && dynamoDB.describeTable("OrderItems").getTable().getGlobalSecondaryIndexes().stream()
                        .anyMatch(index -> "ClaimedDeliveryIdIndex".equals(index.getIndexName()));
        if (indexExists) {
            return;
        }
        dynamoDB.updateTable(new UpdateTableRequest()
                .withTableName("OrderItems")
                .withAttributeDefinitions(new AttributeDefinition("claimedDeliveryId", ScalarAttributeType.S))
                .withGlobalSecondaryIndexUpdates(new GlobalSecondaryIndexUpdate()
                        .withCreate(new CreateGlobalSecondaryIndexAction()
                                .withIndexName("ClaimedDeliveryIdIndex")
                                .withKeySchema(new KeySchemaElement("claimedDeliveryId", KeyType.HASH),
                                        new KeySchemaElement("itemId", KeyType.RANGE))
                                .withProjection(new Projection()
                                        .withProjectionType(ProjectionType.INCLUDE)
                                        .withNonKeyAttributes("status", "deliveryId")))));
    }

    @RollbackExecution
    public void rollback() {
    }
}
