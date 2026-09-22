package pl.commercelink.taxonomy;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBScanExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.ScanResultPage;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;
import com.amazonaws.services.dynamodbv2.model.DeleteItemRequest;
import com.amazonaws.services.dynamodbv2.model.PutItemRequest;
import com.amazonaws.services.dynamodbv2.model.ScanRequest;
import com.amazonaws.services.dynamodbv2.model.Select;
import com.amazonaws.services.dynamodbv2.model.UpdateItemRequest;
import org.springframework.stereotype.Repository;
import pl.commercelink.starter.dynamodb.DynamoDbRepository;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
public class PendingCategorizationRepository extends DynamoDbRepository<PendingCategorization> {

    private static final int SCAN_PAGE_SIZE = 500;

    public PendingCategorizationRepository(AmazonDynamoDB amazonDynamoDB) {
        super(amazonDynamoDB);
    }

    public PendingCategorization find(String mfn) {
        return dynamoDBMapper.load(PendingCategorization.class, mfn);
    }

    public boolean add(String mfn, String supplier, LocalDateTime addedAt) {
        Map<String, AttributeValue> item = new LinkedHashMap<>();
        item.put(PendingCategorization.MFN, new AttributeValue(mfn));
        item.put(PendingCategorization.ATTEMPTS, new AttributeValue().withN("0"));
        item.put(PendingCategorization.ADDED_AT,
                new AttributeValue(addedAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)));
        if (supplier != null && !supplier.isBlank()) {
            item.put(PendingCategorization.SUPPLIER, new AttributeValue(supplier));
        }
        try {
            amazonDynamoDB.putItem(new PutItemRequest()
                    .withTableName(PendingCategorization.TABLE_NAME)
                    .withItem(item)
                    .withConditionExpression("attribute_not_exists(#mfn)")
                    .withExpressionAttributeNames(Map.of("#mfn", PendingCategorization.MFN)));
            return true;
        } catch (ConditionalCheckFailedException e) {
            return false;
        }
    }

    public void recordAttempt(String mfn) {
        amazonDynamoDB.updateItem(new UpdateItemRequest()
                .withTableName(PendingCategorization.TABLE_NAME)
                .withKey(Map.of(PendingCategorization.MFN, new AttributeValue(mfn)))
                .withUpdateExpression("ADD #attempts :one")
                .withExpressionAttributeNames(Map.of("#attempts", PendingCategorization.ATTEMPTS))
                .withExpressionAttributeValues(Map.of(":one", new AttributeValue().withN("1"))));
    }

    public void remove(String mfn) {
        amazonDynamoDB.deleteItem(new DeleteItemRequest()
                .withTableName(PendingCategorization.TABLE_NAME)
                .withKey(Map.of(PendingCategorization.MFN, new AttributeValue(mfn))));
    }

    public List<PendingCategorization> findAll() {
        List<PendingCategorization> all = new ArrayList<>();
        DynamoDBScanExpression expression = new DynamoDBScanExpression().withLimit(SCAN_PAGE_SIZE);

        Map<String, AttributeValue> lastEvaluatedKey = null;
        do {
            expression.setExclusiveStartKey(lastEvaluatedKey);
            ScanResultPage<PendingCategorization> page =
                    dynamoDBMapper.scanPage(PendingCategorization.class, expression);
            all.addAll(page.getResults());
            lastEvaluatedKey = page.getLastEvaluatedKey();
        } while (lastEvaluatedKey != null && !lastEvaluatedKey.isEmpty());

        return all;
    }

    public int count() {
        int total = 0;
        Map<String, AttributeValue> lastEvaluatedKey = null;
        do {
            ScanRequest request = new ScanRequest()
                    .withTableName(PendingCategorization.TABLE_NAME)
                    .withSelect(Select.COUNT)
                    .withExclusiveStartKey(lastEvaluatedKey);
            var result = amazonDynamoDB.scan(request);
            total += result.getCount();
            lastEvaluatedKey = result.getLastEvaluatedKey();
        } while (lastEvaluatedKey != null && !lastEvaluatedKey.isEmpty());
        return total;
    }
}
