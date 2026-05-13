package pl.commercelink.migration;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;

import com.amazonaws.services.dynamodbv2.model.BillingMode;
import com.amazonaws.services.dynamodbv2.model.CreateTableRequest;
import com.amazonaws.services.dynamodbv2.model.GlobalSecondaryIndex;
import com.amazonaws.services.dynamodbv2.model.LocalSecondaryIndex;

import static pl.commercelink.starter.migration.DynamoDbMigrationSupport.createTableIfAbsent;
import static pl.commercelink.starter.migration.DynamoDbMigrationSupport.stringAttribute;
import static pl.commercelink.starter.migration.DynamoDbMigrationSupport.hashKey;
import static pl.commercelink.starter.migration.DynamoDbMigrationSupport.rangeKey;
import static pl.commercelink.starter.migration.DynamoDbMigrationSupport.includeProjection;
import static pl.commercelink.starter.migration.DynamoDbMigrationSupport.allProjection;

import java.util.Arrays;
import java.util.List;

@ChangeUnit(id = "V001-create-dynamodb-tables", order = "001", author = "commercelink")
public class V001_CreateDynamoDbTables {

    private final AmazonDynamoDB dynamoDB;

    public V001_CreateDynamoDbTables(AmazonDynamoDB dynamoDB) {
        this.dynamoDB = dynamoDB;
    }

    @Execution
    public void createTables() {
        createStores();
        createProducts();
        createCatalogs();
        createOrders();
        createOrderItems();
        createOrderEvents();
        createWarehouseItems();
        createBaskets();
        createDeliveries();
        createEmailTemplates();
        createRMA();
        createRMAItems();
        createRMACenters();
        createWarehouseDocuments();
        createWarehouseDocumentSequences();
        createWarehouseDocumentItems();
    }

    @RollbackExecution
    public void rollback() {
    }

    private void createStores() {
        createTableIfAbsent(dynamoDB, new CreateTableRequest()
                .withTableName("Stores")
                .withAttributeDefinitions(stringAttribute("storeId"))
                .withKeySchema(hashKey("storeId"))
                .withBillingMode(BillingMode.PAY_PER_REQUEST));
    }

    private void createProducts() {
        createTableIfAbsent(dynamoDB, new CreateTableRequest()
                .withTableName("Products")
                .withAttributeDefinitions(stringAttribute("categoryId"), stringAttribute("productId"), stringAttribute("pimId"))
                .withKeySchema(hashKey("categoryId"), rangeKey("productId"))
                .withGlobalSecondaryIndexes(new GlobalSecondaryIndex()
                        .withIndexName("PimIdIndex")
                        .withKeySchema(hashKey("pimId"))
                        .withProjection(includeProjection("productId")))
                .withBillingMode(BillingMode.PAY_PER_REQUEST));
    }

    private void createCatalogs() {
        createTableIfAbsent(dynamoDB, new CreateTableRequest()
                .withTableName("Catalogs")
                .withAttributeDefinitions(stringAttribute("storeId"), stringAttribute("catalogId"))
                .withKeySchema(hashKey("storeId"), rangeKey("catalogId"))
                .withBillingMode(BillingMode.PAY_PER_REQUEST));
    }

    private void createOrders() {
        createTableIfAbsent(dynamoDB, new CreateTableRequest()
                .withTableName("Orders")
                .withAttributeDefinitions(
                        stringAttribute("storeId"),
                        stringAttribute("orderId"),
                        stringAttribute("orderedAt"),
                        stringAttribute("externalOrderId"))
                .withKeySchema(hashKey("storeId"), rangeKey("orderId"))
                .withGlobalSecondaryIndexes(
                        new GlobalSecondaryIndex()
                                .withIndexName("StoreIdOrderedAtIndex")
                                .withKeySchema(hashKey("storeId"), rangeKey("orderedAt"))
                                .withProjection(includeProjection("orderId", "status", "email", "fulfilmentType")),
                        new GlobalSecondaryIndex()
                                .withIndexName("ExternalOrderIdIndex")
                                .withKeySchema(hashKey("storeId"), rangeKey("externalOrderId"))
                                .withProjection(includeProjection("orderId")))
                .withBillingMode(BillingMode.PAY_PER_REQUEST));
    }

    private void createOrderItems() {
        createTableIfAbsent(dynamoDB, new CreateTableRequest()
                .withTableName("OrderItems")
                .withAttributeDefinitions(stringAttribute("orderId"), stringAttribute("itemId"))
                .withKeySchema(hashKey("orderId"), rangeKey("itemId"))
                .withBillingMode(BillingMode.PAY_PER_REQUEST));
    }

    private void createOrderEvents() {
        createTableIfAbsent(dynamoDB, new CreateTableRequest()
                .withTableName("OrderEvents")
                .withAttributeDefinitions(stringAttribute("orderId"), stringAttribute("eventId"), stringAttribute("name"))
                .withKeySchema(hashKey("orderId"), rangeKey("eventId"))
                .withLocalSecondaryIndexes(new LocalSecondaryIndex()
                        .withIndexName("NameIndex")
                        .withKeySchema(hashKey("orderId"), rangeKey("name"))
                        .withProjection(allProjection()))
                .withBillingMode(BillingMode.PAY_PER_REQUEST));
    }

    private void createWarehouseItems() {
        createTableIfAbsent(dynamoDB, new CreateTableRequest()
                .withTableName("WarehouseItems")
                .withAttributeDefinitions(stringAttribute("storeId"), stringAttribute("itemId"))
                .withKeySchema(hashKey("storeId"), rangeKey("itemId"))
                .withBillingMode(BillingMode.PAY_PER_REQUEST));
    }

    private void createBaskets() {
        createTableIfAbsent(dynamoDB, new CreateTableRequest()
                .withTableName("Baskets")
                .withAttributeDefinitions(stringAttribute("storeId"), stringAttribute("basketId"), stringAttribute("createdAt"))
                .withKeySchema(hashKey("storeId"), rangeKey("basketId"))
                .withGlobalSecondaryIndexes(new GlobalSecondaryIndex()
                        .withIndexName("BasketCreatedAtIndex")
                        .withKeySchema(hashKey("storeId"), rangeKey("createdAt"))
                        .withProjection(includeProjection("basketId", "name", "type", "expiresAt")))
                .withBillingMode(BillingMode.PAY_PER_REQUEST));
    }

    private void createDeliveries() {
        createTableIfAbsent(dynamoDB, new CreateTableRequest()
                .withTableName("Deliveries")
                .withAttributeDefinitions(stringAttribute("storeId"), stringAttribute("deliveryId"))
                .withKeySchema(hashKey("storeId"), rangeKey("deliveryId"))
                .withBillingMode(BillingMode.PAY_PER_REQUEST));
    }

    private void createEmailTemplates() {
        createTableIfAbsent(dynamoDB, new CreateTableRequest()
                .withTableName("EmailTemplates")
                .withAttributeDefinitions(stringAttribute("storeId"), stringAttribute("templateName"))
                .withKeySchema(hashKey("storeId"), rangeKey("templateName"))
                .withBillingMode(BillingMode.PAY_PER_REQUEST));
    }

    private void createRMA() {
        createTableIfAbsent(dynamoDB, new CreateTableRequest()
                .withTableName("RMA")
                .withAttributeDefinitions(stringAttribute("storeId"), stringAttribute("rmaId"))
                .withKeySchema(hashKey("storeId"), rangeKey("rmaId"))
                .withBillingMode(BillingMode.PAY_PER_REQUEST));
    }

    private void createRMAItems() {
        createTableIfAbsent(dynamoDB, new CreateTableRequest()
                .withTableName("RMAItems")
                .withAttributeDefinitions(stringAttribute("rmaId"), stringAttribute("rmaItemId"))
                .withKeySchema(hashKey("rmaId"), rangeKey("rmaItemId"))
                .withBillingMode(BillingMode.PAY_PER_REQUEST));
    }

    private void createRMACenters() {
        createTableIfAbsent(dynamoDB, new CreateTableRequest()
                .withTableName("RMACenters")
                .withAttributeDefinitions(stringAttribute("storeId"), stringAttribute("rmaCenterId"))
                .withKeySchema(hashKey("storeId"), rangeKey("rmaCenterId"))
                .withBillingMode(BillingMode.PAY_PER_REQUEST));
    }

    private void createWarehouseDocuments() {
        createTableIfAbsent(dynamoDB, new CreateTableRequest()
                .withTableName("WarehouseDocuments")
                .withAttributeDefinitions(
                        stringAttribute("storeId"),
                        stringAttribute("documentId"),
                        stringAttribute("deliveryId"),
                        stringAttribute("orderId"),
                        stringAttribute("rmaId"),
                        stringAttribute("createdAt"))
                .withKeySchema(hashKey("storeId"), rangeKey("documentId"))
                .withGlobalSecondaryIndexes(
                        new GlobalSecondaryIndex()
                                .withIndexName("DeliveryIdIndex")
                                .withKeySchema(hashKey("storeId"), rangeKey("deliveryId"))
                                .withProjection(allProjection()),
                        new GlobalSecondaryIndex()
                                .withIndexName("CreatedAtIndex")
                                .withKeySchema(hashKey("storeId"), rangeKey("createdAt"))
                                .withProjection(allProjection()),
                        new GlobalSecondaryIndex()
                                .withIndexName("OrderIdIndex")
                                .withKeySchema(hashKey("storeId"), rangeKey("orderId"))
                                .withProjection(allProjection()),
                        new GlobalSecondaryIndex()
                                .withIndexName("RMAIdIndex")
                                .withKeySchema(hashKey("storeId"), rangeKey("rmaId"))
                                .withProjection(allProjection()))
                .withBillingMode(BillingMode.PAY_PER_REQUEST));
    }

    private void createWarehouseDocumentSequences() {
        createTableIfAbsent(dynamoDB, new CreateTableRequest()
                .withTableName("WarehouseDocumentSequences")
                .withAttributeDefinitions(stringAttribute("storeId"), stringAttribute("sequenceKey"))
                .withKeySchema(hashKey("storeId"), rangeKey("sequenceKey"))
                .withBillingMode(BillingMode.PAY_PER_REQUEST));
    }

    private void createWarehouseDocumentItems() {
        createTableIfAbsent(dynamoDB, new CreateTableRequest()
                .withTableName("WarehouseDocumentItems")
                .withAttributeDefinitions(stringAttribute("documentId"), stringAttribute("itemId"), stringAttribute("deliveryId"))
                .withKeySchema(hashKey("documentId"), rangeKey("itemId"))
                .withGlobalSecondaryIndexes(new GlobalSecondaryIndex()
                        .withIndexName("DeliveryIdIndex")
                        .withKeySchema(hashKey("deliveryId"))
                        .withProjection(allProjection()))
                .withBillingMode(BillingMode.PAY_PER_REQUEST));
    }

}
