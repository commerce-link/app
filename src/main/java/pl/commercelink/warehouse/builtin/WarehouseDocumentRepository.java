package pl.commercelink.warehouse.builtin;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTransactionWriteExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.TransactionWriteRequest;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.TransactionCanceledException;
import org.springframework.stereotype.Component;
import pl.commercelink.documents.DocumentReason;
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

    WarehouseDocument findByDocumentId(String storeId, String documentId) {
        return dynamoDBMapper.load(WarehouseDocument.class, storeId, documentId);
    }

    List<WarehouseDocument> search(WarehouseDocumentCriteria criteria, int page, int pageSize) {
        return queryWithPagination(buildSearchQuery(criteria), page, pageSize, WarehouseDocument.class);
    }

    List<WarehouseDocument> findAllMatching(WarehouseDocumentCriteria criteria) {
        return dynamoDBMapper.query(WarehouseDocument.class, buildSearchQuery(criteria));
    }

    private DynamoDBQueryExpression<WarehouseDocument> buildSearchQuery(WarehouseDocumentCriteria criteria) {
        Map<String, AttributeValue> eav = new HashMap<>();
        Map<String, String> names = new HashMap<>();
        eav.put(":storeId", new AttributeValue().withS(criteria.storeId()));

        StringBuilder keyCondition = new StringBuilder("storeId = :storeId");
        // each date works on its own; the list used to ignore a single date
        if (criteria.from() != null && criteria.to() != null) {
            eav.put(":dateFrom", new AttributeValue().withS(criteria.from().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)));
            eav.put(":dateTo", new AttributeValue().withS(criteria.to().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)));
            keyCondition.append(" AND createdAt BETWEEN :dateFrom AND :dateTo");
        } else if (criteria.from() != null) {
            eav.put(":dateFrom", new AttributeValue().withS(criteria.from().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)));
            keyCondition.append(" AND createdAt >= :dateFrom");
        } else if (criteria.to() != null) {
            eav.put(":dateTo", new AttributeValue().withS(criteria.to().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)));
            keyCondition.append(" AND createdAt <= :dateTo");
        }

        StringBuilder filterExpression = new StringBuilder();
        if (criteria.type() != null) {
            eav.put(":type", new AttributeValue().withS(criteria.type().name()));
            names.put("#type", "type");
            appendFilter(filterExpression, "#type = :type");
        }
        if (!criteria.reasons().isEmpty()) {
            List<String> placeholders = new ArrayList<>();
            int i = 0;
            for (DocumentReason reason : criteria.reasons()) {
                String placeholder = ":reason" + i++;
                eav.put(placeholder, new AttributeValue().withS(reason.name()));
                placeholders.add(placeholder);
            }
            names.put("#reason", "reason");
            appendFilter(filterExpression, "#reason IN (" + String.join(", ", placeholders) + ")");
        }
        if (criteria.numberFragment() != null) {
            eav.put(":number", new AttributeValue().withS(criteria.numberFragment()));
            appendFilter(filterExpression, "contains(documentNo, :number)");
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
        if (!names.isEmpty()) {
            queryExpression.withExpressionAttributeNames(names);
        }
        return queryExpression;
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
        request.addPut(document);
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
}
