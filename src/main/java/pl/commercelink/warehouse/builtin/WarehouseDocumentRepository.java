package pl.commercelink.warehouse.builtin;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTransactionWriteExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.TransactionWriteRequest;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.TransactionCanceledException;
import org.springframework.stereotype.Component;
import pl.commercelink.documents.DocumentType;
import pl.commercelink.starter.dynamodb.DynamoDbRepository;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;

@Component
class WarehouseDocumentRepository extends DynamoDbRepository<WarehouseDocument> {

    WarehouseDocumentRepository(AmazonDynamoDB amazonDynamoDB) {
        super(amazonDynamoDB);
    }

    WarehouseDocument findByDocumentNo(String storeId, String documentNo) {
        return dynamoDBMapper.load(WarehouseDocument.class, storeId, documentNo);
    }

    List<WarehouseDocument> search(String storeId, DocumentType type, LocalDateTime dateFrom,
                                          LocalDateTime dateTo, String warehouseId, int page, int pageSize) {
        Map<String, AttributeValue> eav = new HashMap<>();
        eav.put(":storeId", new AttributeValue().withS(storeId));

        StringBuilder filterExpression = new StringBuilder();
        StringBuilder keyCondition = new StringBuilder("storeId = :storeId");

        if (type != null) {
            eav.put(":type", new AttributeValue().withS(type.name()));
            appendFilter(filterExpression, "#type = :type");
        }

        if (warehouseId != null && !warehouseId.trim().isEmpty()) {
            eav.put(":warehouseId", new AttributeValue().withS(warehouseId));
            appendFilter(filterExpression, "warehouseId = :warehouseId");
        }

        if (dateFrom != null && dateTo != null) {
            eav.put(":dateFrom", new AttributeValue().withS(dateFrom.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)));
            eav.put(":dateTo", new AttributeValue().withS(dateTo.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)));
            keyCondition.append(" AND createdAt BETWEEN :dateFrom AND :dateTo");
        }

        DynamoDBQueryExpression<WarehouseDocument> queryExpression = new DynamoDBQueryExpression<WarehouseDocument>()
                .withIndexName("CreatedAtIndex")
                .withConsistentRead(false)
                .withKeyConditionExpression(keyCondition.toString())
                .withScanIndexForward(false)
                .withExpressionAttributeValues(eav);

        if (filterExpression.length() > 0) {
            queryExpression.withFilterExpression(filterExpression.toString());
        }

        if (type != null) {
            Map<String, String> ean = new HashMap<>();
            ean.put("#type", "type");
            queryExpression.withExpressionAttributeNames(ean);
        }

        return queryWithPagination(queryExpression, page, pageSize, WarehouseDocument.class);
    }

    List<WarehouseDocument> findAllBeforeDate(String storeId, LocalDateTime dateTo) {
        Map<String, AttributeValue> eav = new HashMap<>();
        eav.put(":storeId", new AttributeValue().withS(storeId));
        eav.put(":dateTo", new AttributeValue().withS(dateTo.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)));

        DynamoDBQueryExpression<WarehouseDocument> queryExpression = new DynamoDBQueryExpression<WarehouseDocument>()
                .withIndexName("CreatedAtIndex")
                .withConsistentRead(false)
                .withKeyConditionExpression("storeId = :storeId AND createdAt < :dateTo")
                .withExpressionAttributeValues(eav);

        return new ArrayList<>(dynamoDBMapper.query(WarehouseDocument.class, queryExpression));
    }

    List<WarehouseDocument> findAllInDateRange(String storeId, LocalDateTime dateFrom, LocalDateTime dateTo) {
        Map<String, AttributeValue> eav = new HashMap<>();
        eav.put(":storeId", new AttributeValue().withS(storeId));
        eav.put(":dateFrom", new AttributeValue().withS(dateFrom.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)));
        eav.put(":dateTo", new AttributeValue().withS(dateTo.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)));

        DynamoDBQueryExpression<WarehouseDocument> queryExpression = new DynamoDBQueryExpression<WarehouseDocument>()
                .withIndexName("CreatedAtIndex")
                .withConsistentRead(false)
                .withKeyConditionExpression("storeId = :storeId AND createdAt BETWEEN :dateFrom AND :dateTo")
                .withExpressionAttributeValues(eav);

        return new ArrayList<>(dynamoDBMapper.query(WarehouseDocument.class, queryExpression));
    }

    List<WarehouseDocument> findByDeliveryId(String storeId, String deliveryId) {
        Map<String, AttributeValue> eav = new HashMap<>();
        eav.put(":storeId", new AttributeValue().withS(storeId));
        eav.put(":deliveryId", new AttributeValue().withS(deliveryId));

        DynamoDBQueryExpression<WarehouseDocument> queryExpression = new DynamoDBQueryExpression<WarehouseDocument>()
                .withIndexName("DeliveryIdIndex")
                .withConsistentRead(false)
                .withKeyConditionExpression("storeId = :storeId AND deliveryId = :deliveryId")
                .withExpressionAttributeValues(eav);

        return dynamoDBMapper.query(WarehouseDocument.class, queryExpression);
    }

    Optional<WarehouseDocument> saveWithSequence(
            String storeId,
            String sequenceKey,
            int maxRetries,
            Function<String, WarehouseDocument> documentFactory
    ) {
        for (int attempt = 0; attempt < maxRetries; attempt++) {
            long currentValue = getCurrentSequenceValue(storeId, sequenceKey);
            long nextValue = currentValue + 1;

            WarehouseDocument document = documentFactory.apply(sequenceKey + "/" + String.format("%06d", nextValue));
            WarehouseDocumentSequence sequence = new WarehouseDocumentSequence(storeId, sequenceKey, nextValue);

            try {
                executeTransaction(document, sequence, currentValue);
                return Optional.of(document);
            } catch (TransactionCanceledException e) {
                // Conflict - retry with fresh sequence value
            }
        }
        return Optional.empty();
    }

    private long getCurrentSequenceValue(String storeId, String sequenceKey) {
        WarehouseDocumentSequence sequence = dynamoDBMapper.load(WarehouseDocumentSequence.class, storeId, sequenceKey);
        return sequence == null ? 0 : sequence.getCurrentValue();
    }

    private void executeTransaction(WarehouseDocument document, WarehouseDocumentSequence sequence, long expectedSequenceValue) {
        TransactionWriteRequest request = new TransactionWriteRequest();
        request.addPut(sequence, buildSequenceCondition(expectedSequenceValue));
        request.addPut(document, buildDocumentCondition());
        dynamoDBMapper.transactionWrite(request);
    }

    private DynamoDBTransactionWriteExpression buildSequenceCondition(long expectedValue) {
        DynamoDBTransactionWriteExpression expression = new DynamoDBTransactionWriteExpression();
        if (expectedValue == 0) {
            expression.withConditionExpression("attribute_not_exists(currentValue)");
        } else {
            expression.withConditionExpression("currentValue = :expectedValue")
                    .withExpressionAttributeValues(
                            Collections.singletonMap(":expectedValue", new AttributeValue().withN(String.valueOf(expectedValue)))
                    );
        }
        return expression;
    }

    private DynamoDBTransactionWriteExpression buildDocumentCondition() {
        return new DynamoDBTransactionWriteExpression()
                .withConditionExpression("attribute_not_exists(documentNo)");
    }
}
