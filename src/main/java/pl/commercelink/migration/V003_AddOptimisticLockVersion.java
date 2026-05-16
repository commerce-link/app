package pl.commercelink.migration;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;

import java.util.List;
import java.util.Map;

import static pl.commercelink.starter.migration.DynamoDbMigrationSupport.backfillAttributes;

@ChangeUnit(id = "V003-add-optimistic-lock-version", order = "003", author = "commercelink")
public class V003_AddOptimisticLockVersion {

    private static final String VERSION_ATTR = "version";
    private static final String UPDATE_EXPRESSION = "SET #v = if_not_exists(#v, :one)";
    private static final Map<String, String> EXPRESSION_ATTRIBUTE_NAMES = Map.of("#v", VERSION_ATTR);
    private static final Map<String, AttributeValue> EXPRESSION_ATTRIBUTE_VALUES =
            Map.of(":one", new AttributeValue().withN("1"));

    private final AmazonDynamoDB dynamoDB;

    public V003_AddOptimisticLockVersion(AmazonDynamoDB dynamoDB) {
        this.dynamoDB = dynamoDB;
    }

    @Execution
    public void backfillVersions() {
        backfillVersion("Stores", List.of("storeId"));
        backfillVersion("Products", List.of("categoryId", "productId"));
        backfillVersion("Catalogs", List.of("storeId", "catalogId"));
        backfillVersion("Orders", List.of("storeId", "orderId"));
        backfillVersion("OrderItems", List.of("orderId", "itemId"));
        backfillVersion("OrderEvents", List.of("orderId", "eventId"));
        backfillVersion("WarehouseItems", List.of("storeId", "itemId"));
        backfillVersion("Baskets", List.of("storeId", "basketId"));
        backfillVersion("Deliveries", List.of("storeId", "deliveryId"));
        backfillVersion("EmailTemplates", List.of("storeId", "templateName"));
        backfillVersion("RMA", List.of("storeId", "rmaId"));
        backfillVersion("RMAItems", List.of("rmaId", "rmaItemId"));
        backfillVersion("RMACenters", List.of("storeId", "rmaCenterId"));
        backfillVersion("WarehouseDocuments", List.of("storeId", "documentId"));
        backfillVersion("WarehouseDocumentItems", List.of("documentId", "itemId"));
    }

    @RollbackExecution
    public void rollback() {
    }

    private void backfillVersion(String tableName, List<String> keyNames) {
        backfillAttributes(dynamoDB, tableName, keyNames, UPDATE_EXPRESSION, EXPRESSION_ATTRIBUTE_NAMES,
                EXPRESSION_ATTRIBUTE_VALUES);
    }
}
