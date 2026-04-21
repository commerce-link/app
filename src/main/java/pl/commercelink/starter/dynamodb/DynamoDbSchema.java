package pl.commercelink.starter.dynamodb;

import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.model.*;

public class DynamoDbSchema {

    public static void main(String[] args) {
        AmazonDynamoDB dynamoDB = AmazonDynamoDBClientBuilder.standard()
                .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration("http://localhost:8000", "eu-central-1"))
                .build();

        dynamoDB.createTable(storeTableSchema());
        dynamoDB.createTable(productsV2TableSchema());
        dynamoDB.createTable(productCatalogTableSchema());
        dynamoDB.createTable(ordersTableSchema());
        dynamoDB.createTable(orderItemsSchema());
        dynamoDB.createTable(warehouseTableSchema());
        dynamoDB.createTable(basketsTableSchema());
        dynamoDB.createTable(deliveryTableSchema());
        dynamoDB.createTable(emailTemplatesTableSchema());
        dynamoDB.createTable(rmaTableSchema());
        dynamoDB.createTable(rmaItemsTableSchema());
        dynamoDB.createTable(rmaCentersTableSchema());

        dynamoDB.createTable(warehouseDocumentsTableSchema());
        dynamoDB.createTable(warehouseDocumentsSequencesTableSchema());


        dynamoDB.createTable(warehouseDocumentItemsTableSchema());
        dynamoDB.createTable(orderEventsTableSchema());
        addExternalOrderIdIndexForOrderSchema(dynamoDB); // not created yet
        addPimIdIndexForProductsSchema(dynamoDB);
        addBasketCreatedAtIndexForBasketSchema(dynamoDB);
        recreateStoreIdOrderedAtIndex(dynamoDB);
    }

    public static CreateTableRequest storeTableSchema() {
        return new CreateTableRequest()
                .withTableName("Stores")
                .withKeySchema(new KeySchemaElement("storeId", KeyType.HASH)) // Only partition key
                .withAttributeDefinitions(
                        new AttributeDefinition("storeId", ScalarAttributeType.S)
                )
                .withBillingMode(BillingMode.PAY_PER_REQUEST); // On-demand mode
    }

    public static CreateTableRequest productsV2TableSchema() {
        return new CreateTableRequest()
                .withTableName("ProductsV2")
                .withKeySchema(new KeySchemaElement("categoryId", KeyType.HASH), // Partition key
                        new KeySchemaElement("productId", KeyType.RANGE)) // Sort key
                .withAttributeDefinitions(
                        new AttributeDefinition("categoryId", ScalarAttributeType.S),
                        new AttributeDefinition("productId", ScalarAttributeType.S),
                        new AttributeDefinition("pimId", ScalarAttributeType.S)
                )
                .withBillingMode(BillingMode.PAY_PER_REQUEST) // On-demand mode
                .withGlobalSecondaryIndexes(new GlobalSecondaryIndex()
                        .withIndexName("PimIdIndex")
                        .withKeySchema(
                                new KeySchemaElement("pimId", KeyType.HASH)
                        )
                        .withProjection(new Projection()
                                .withProjectionType(ProjectionType.INCLUDE)
                                .withNonKeyAttributes("productId"))
                        );
    }

    public static void addPimIdIndexForProductsSchema(AmazonDynamoDB dynamoDB) {
        UpdateTableRequest updateTableRequest = new UpdateTableRequest()
                .withTableName("Products")
                .withAttributeDefinitions(
                        new AttributeDefinition("pimId", ScalarAttributeType.S)
                )
                .withGlobalSecondaryIndexUpdates(new GlobalSecondaryIndexUpdate()
                        .withCreate(new CreateGlobalSecondaryIndexAction()
                                .withIndexName("PimIdIndex")
                                .withKeySchema(
                                        new KeySchemaElement("pimId", KeyType.HASH)
                                )
                                .withProjection(new Projection()
                                        .withProjectionType(ProjectionType.INCLUDE)
                                        .withNonKeyAttributes("productId"))
                        ));

        dynamoDB.updateTable(updateTableRequest);
    }


    public static CreateTableRequest productCatalogTableSchema() {
        return new CreateTableRequest()
                .withTableName("Catalogs")
                .withKeySchema(new KeySchemaElement("storeId", KeyType.HASH), // Partition key
                        new KeySchemaElement("catalogId", KeyType.RANGE)) // Sort key
                .withAttributeDefinitions(
                        new AttributeDefinition("storeId", ScalarAttributeType.S),
                        new AttributeDefinition("catalogId", ScalarAttributeType.S)
                )
                .withBillingMode(BillingMode.PAY_PER_REQUEST); // On-demand mode
    }

    public static CreateTableRequest ordersTableSchema() {
        return new CreateTableRequest()
                .withTableName("Orders")
                .withKeySchema(new KeySchemaElement("storeId", KeyType.HASH), // Partition key
                        new KeySchemaElement("orderId", KeyType.RANGE)) // Sort key
                .withAttributeDefinitions(
                        new AttributeDefinition("storeId", ScalarAttributeType.S),
                        new AttributeDefinition("orderId", ScalarAttributeType.S),
                        new AttributeDefinition("orderedAt", ScalarAttributeType.S)
                )
                .withBillingMode(BillingMode.PAY_PER_REQUEST) // On-demand mode
                .withGlobalSecondaryIndexes(new GlobalSecondaryIndex()
                        .withIndexName("StoreIdOrderedAtIndex")
                        .withKeySchema(
                                new KeySchemaElement("storeId", KeyType.HASH),
                                new KeySchemaElement("orderedAt", KeyType.RANGE)
                        )
                        .withProjection(new Projection()
                                .withProjectionType(ProjectionType.INCLUDE)
                                .withNonKeyAttributes("orderId", "status", "email", "fulfilmentType")));
    }

    public static CreateTableRequest warehouseTableSchema() {
        return new CreateTableRequest()
                .withTableName("WarehouseItems")
                .withKeySchema(new KeySchemaElement("storeId", KeyType.HASH), // Partition key
                        new KeySchemaElement("itemId", KeyType.RANGE)) // Sort key
                .withAttributeDefinitions(
                        new AttributeDefinition("storeId", ScalarAttributeType.S),
                        new AttributeDefinition("itemId", ScalarAttributeType.S)
                )
                .withBillingMode(BillingMode.PAY_PER_REQUEST); // On-demand mode
    }

    public static CreateTableRequest basketsTableSchema() {
        return new CreateTableRequest()
                .withTableName("Baskets")
                .withKeySchema(new KeySchemaElement("storeId", KeyType.HASH),
                        new KeySchemaElement("basketId", KeyType.RANGE))
                .withAttributeDefinitions(
                        new AttributeDefinition("storeId", ScalarAttributeType.S),
                        new AttributeDefinition("basketId", ScalarAttributeType.S)
                )
                .withBillingMode(BillingMode.PAY_PER_REQUEST);
    }

    public static CreateTableRequest orderItemsSchema() {
        return new CreateTableRequest()
                .withTableName("OrderItems")
                .withKeySchema(
                        new KeySchemaElement("orderId", KeyType.HASH),
                        new KeySchemaElement("itemId", KeyType.RANGE)
                )
                .withAttributeDefinitions(
                        new AttributeDefinition("orderId", ScalarAttributeType.S),
                        new AttributeDefinition("itemId", ScalarAttributeType.S)
                )
                .withBillingMode(BillingMode.PAY_PER_REQUEST);
    }

    public static void addExternalOrderIdIndexForOrderSchema(AmazonDynamoDB dynamoDB) {
        UpdateTableRequest updateTableRequest = new UpdateTableRequest()
                .withTableName("Orders")
                .withAttributeDefinitions(
                        new AttributeDefinition("externalOrderId", ScalarAttributeType.S),
                        new AttributeDefinition("storeId", ScalarAttributeType.S)
                )
                .withGlobalSecondaryIndexUpdates(new GlobalSecondaryIndexUpdate()
                        .withCreate(new CreateGlobalSecondaryIndexAction()
                                .withIndexName("ExternalOrderIdIndex")
                                .withKeySchema(
                                        new KeySchemaElement("storeId", KeyType.HASH),
                                        new KeySchemaElement("externalOrderId", KeyType.RANGE)
                                )
                                .withProjection(new Projection()
                                        .withProjectionType(ProjectionType.INCLUDE)
                                        .withNonKeyAttributes("orderId"))
                        ));

        dynamoDB.updateTable(updateTableRequest);
    }

    public static CreateTableRequest deliveryTableSchema() {
        return new CreateTableRequest()
                .withTableName("Deliveries")
                .withKeySchema(
                        new KeySchemaElement("storeId", KeyType.HASH),
                        new KeySchemaElement("deliveryId", KeyType.RANGE)
                )
                .withAttributeDefinitions(
                        new AttributeDefinition("storeId", ScalarAttributeType.S),
                        new AttributeDefinition("deliveryId", ScalarAttributeType.S)
                )
                .withBillingMode(BillingMode.PAY_PER_REQUEST);
    }

    public static void addBasketCreatedAtIndexForBasketSchema(AmazonDynamoDB dynamoDB) {
        UpdateTableRequest updateTableRequest = new UpdateTableRequest()
                .withTableName("Baskets")
                .withAttributeDefinitions(
                        new AttributeDefinition("storeId", ScalarAttributeType.S),
                        new AttributeDefinition("createdAt", ScalarAttributeType.S)
                )
                .withGlobalSecondaryIndexUpdates(new GlobalSecondaryIndexUpdate()
                        .withCreate(new CreateGlobalSecondaryIndexAction()
                                .withIndexName("BasketCreatedAtIndex")
                                .withKeySchema(
                                        new KeySchemaElement("storeId", KeyType.HASH),
                                        new KeySchemaElement("createdAt", KeyType.RANGE)
                                )
                                .withProjection(new Projection()
                                        .withProjectionType(ProjectionType.INCLUDE)
                                        .withNonKeyAttributes("basketId", "name", "type", "expiresAt"))
                        ));

        dynamoDB.updateTable(updateTableRequest);
    }

    public static CreateTableRequest emailTemplatesTableSchema() {
        return new CreateTableRequest()
                .withTableName("EmailTemplates")
                .withKeySchema(
                        new KeySchemaElement("storeId", KeyType.HASH),
                        new KeySchemaElement("templateName", KeyType.RANGE)
                )
                .withAttributeDefinitions(
                        new AttributeDefinition("storeId", ScalarAttributeType.S),
                        new AttributeDefinition("templateName", ScalarAttributeType.S)
                )
                .withBillingMode(BillingMode.PAY_PER_REQUEST);
    }

    public static CreateTableRequest rmaTableSchema() {
        return new CreateTableRequest()
                .withTableName("RMA")
                .withKeySchema(
                        new KeySchemaElement("storeId", KeyType.HASH),
                        new KeySchemaElement("rmaId", KeyType.RANGE)
                )
                .withAttributeDefinitions(
                        new AttributeDefinition("storeId", ScalarAttributeType.S),
                        new AttributeDefinition("rmaId", ScalarAttributeType.S)
                )
                .withBillingMode(BillingMode.PAY_PER_REQUEST);
    }

    public static CreateTableRequest rmaItemsTableSchema() {
        return new CreateTableRequest()
                .withTableName("RMAItems")
                .withKeySchema(
                        new KeySchemaElement("rmaId", KeyType.HASH),
                        new KeySchemaElement("rmaItemId", KeyType.RANGE)
                )
                .withAttributeDefinitions(
                        new AttributeDefinition("rmaId", ScalarAttributeType.S),
                        new AttributeDefinition("rmaItemId", ScalarAttributeType.S)
                )
                .withBillingMode(BillingMode.PAY_PER_REQUEST);
    }

    public static CreateTableRequest rmaCentersTableSchema() {
        return new CreateTableRequest()
                .withTableName("RMACenters")
                .withKeySchema(
                        new KeySchemaElement("storeId", KeyType.HASH),
                        new KeySchemaElement("rmaCenterId", KeyType.RANGE)
                )
                .withAttributeDefinitions(
                        new AttributeDefinition("storeId", ScalarAttributeType.S),
                        new AttributeDefinition("rmaCenterId", ScalarAttributeType.S)
                )
                .withBillingMode(BillingMode.PAY_PER_REQUEST);
    }

    public static void recreateStoreIdOrderedAtIndex(AmazonDynamoDB dynamoDBClient) {
        final String tableName = "Orders";
        final String indexName = "StoreIdOrderedAtIndex";

        try {
            UpdateTableRequest deleteIndexRequest = new UpdateTableRequest()
                    .withTableName(tableName)
                    .withGlobalSecondaryIndexUpdates(
                            new GlobalSecondaryIndexUpdate()
                                    .withDelete(new DeleteGlobalSecondaryIndexAction().withIndexName(indexName))
                    );
            dynamoDBClient.updateTable(deleteIndexRequest);

            boolean deleted = false;
            while (!deleted) {
                Thread.sleep(5000);
                TableDescription tableDescription = dynamoDBClient.describeTable(tableName).getTable();
                deleted = tableDescription.getGlobalSecondaryIndexes() == null ||
                        tableDescription.getGlobalSecondaryIndexes().stream()
                                .noneMatch(gsi -> gsi.getIndexName().equals(indexName));
            }

            UpdateTableRequest recreateIndexRequest = new UpdateTableRequest()
                    .withTableName(tableName)
                    .withAttributeDefinitions(
                            new AttributeDefinition("storeId", ScalarAttributeType.S),
                            new AttributeDefinition("orderedAt", ScalarAttributeType.S)
                    )
                    .withGlobalSecondaryIndexUpdates(
                            new GlobalSecondaryIndexUpdate()
                                    .withCreate(new CreateGlobalSecondaryIndexAction()
                                            .withIndexName(indexName)
                                            .withKeySchema(
                                                    new KeySchemaElement("storeId", KeyType.HASH),
                                                    new KeySchemaElement("orderedAt", KeyType.RANGE)
                                            )
                                            .withProjection(new Projection()
                                                    .withProjectionType(ProjectionType.INCLUDE)
                                                    .withNonKeyAttributes("orderId", "status", "email", "fulfilmentType"))
                                    )
                    );
            dynamoDBClient.updateTable(recreateIndexRequest);
        } catch (InterruptedException e) {
            throw new RuntimeException();
        }
    }

    public static CreateTableRequest warehouseDocumentsTableSchema() {
        return new CreateTableRequest()
                .withTableName("WarehouseDocuments")
                .withKeySchema(
                        new KeySchemaElement("storeId", KeyType.HASH),
                        new KeySchemaElement("documentNo", KeyType.RANGE)
                )
                .withAttributeDefinitions(
                        new AttributeDefinition("storeId", ScalarAttributeType.S),
                        new AttributeDefinition("documentNo", ScalarAttributeType.S),
                        new AttributeDefinition("deliveryId", ScalarAttributeType.S),
                        new AttributeDefinition("createdAt", ScalarAttributeType.S)
                )
                .withBillingMode(BillingMode.PAY_PER_REQUEST)
                .withGlobalSecondaryIndexes(
                        new GlobalSecondaryIndex()
                                .withIndexName("DeliveryIdIndex")
                                .withKeySchema(
                                        new KeySchemaElement("storeId", KeyType.HASH),
                                        new KeySchemaElement("deliveryId", KeyType.RANGE)
                                )
                                .withProjection(new Projection().withProjectionType(ProjectionType.ALL)),
                        new GlobalSecondaryIndex()
                                .withIndexName("CreatedAtIndex")
                                .withKeySchema(
                                        new KeySchemaElement("storeId", KeyType.HASH),
                                        new KeySchemaElement("createdAt", KeyType.RANGE)
                                )
                                .withProjection(new Projection().withProjectionType(ProjectionType.ALL))
                );
    }

    public static CreateTableRequest warehouseDocumentsSequencesTableSchema() {
        return new CreateTableRequest()
                .withTableName("WarehouseDocumentSequences")
                .withKeySchema(
                        new KeySchemaElement("storeId", KeyType.HASH),
                        new KeySchemaElement("sequenceKey", KeyType.RANGE)
                )
                .withAttributeDefinitions(
                        new AttributeDefinition("storeId", ScalarAttributeType.S),
                        new AttributeDefinition("sequenceKey", ScalarAttributeType.S)
                )
                .withBillingMode(BillingMode.PAY_PER_REQUEST);
    }

    public static CreateTableRequest orderEventsTableSchema() {
        return new CreateTableRequest()
                .withTableName("OrderEvents")
                .withKeySchema(
                        new KeySchemaElement("orderId", KeyType.HASH),
                        new KeySchemaElement("eventId", KeyType.RANGE)
                )
                .withAttributeDefinitions(
                        new AttributeDefinition("orderId", ScalarAttributeType.S),
                        new AttributeDefinition("eventId", ScalarAttributeType.S),
                        new AttributeDefinition("name", ScalarAttributeType.S)
                )
                .withBillingMode(BillingMode.PAY_PER_REQUEST)
                .withLocalSecondaryIndexes(new LocalSecondaryIndex()
                        .withIndexName("NameIndex")
                        .withKeySchema(
                                new KeySchemaElement("orderId", KeyType.HASH),
                                new KeySchemaElement("name", KeyType.RANGE)
                        )
                        .withProjection(new Projection().withProjectionType(ProjectionType.ALL)));
    }

    public static CreateTableRequest warehouseDocumentItemsTableSchema() {
        return new CreateTableRequest()
                .withTableName("WarehouseDocumentItems")
                .withKeySchema(
                        new KeySchemaElement("documentNo", KeyType.HASH),
                        new KeySchemaElement("itemId", KeyType.RANGE)
                )
                .withAttributeDefinitions(
                        new AttributeDefinition("documentNo", ScalarAttributeType.S),
                        new AttributeDefinition("itemId", ScalarAttributeType.S),
                        new AttributeDefinition("deliveryId", ScalarAttributeType.S),
                        new AttributeDefinition("mfn", ScalarAttributeType.S)
                )
                .withBillingMode(BillingMode.PAY_PER_REQUEST)
                .withGlobalSecondaryIndexes(
                        new GlobalSecondaryIndex()
                                .withIndexName("DeliveryIdIndex")
                                .withKeySchema(
                                        new KeySchemaElement("deliveryId", KeyType.HASH)
                                )
                                .withProjection(new Projection().withProjectionType(ProjectionType.ALL)),
                        new GlobalSecondaryIndex()
                                .withIndexName("MfnIndex")
                                .withKeySchema(
                                        new KeySchemaElement("mfn", KeyType.HASH)
                                )
                                .withProjection(new Projection().withProjectionType(ProjectionType.ALL))
                );
    }

}
