package pl.commercelink.taxonomy;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBScanExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.ScanResultPage;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;
import com.amazonaws.services.dynamodbv2.model.UpdateItemRequest;
import org.springframework.stereotype.Repository;
import pl.commercelink.starter.dynamodb.DynamoDbRepository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

@Repository
public class TaxonomyCatalogRepository extends DynamoDbRepository<TaxonomyItem> {

    static final int BATCH_GET_SIZE = 100;
    private static final int SCAN_PAGE_SIZE = 500;

    public TaxonomyCatalogRepository(AmazonDynamoDB amazonDynamoDB) {
        super(amazonDynamoDB);
    }

    public Taxonomy find(String mfn) {
        TaxonomyItem item = dynamoDBMapper.load(TaxonomyItem.class, mfn);
        return item == null ? null : item.toTaxonomy();
    }

    public Map<String, Taxonomy> findAll(List<String> mfns) {
        List<List<String>> chunks = new ArrayList<>();
        for (int from = 0; from < mfns.size(); from += BATCH_GET_SIZE) {
            chunks.add(mfns.subList(from, Math.min(from + BATCH_GET_SIZE, mfns.size())));
        }
        Map<String, Taxonomy> found = new ConcurrentHashMap<>();
        chunks.parallelStream()
                .forEach(chunk -> loadChunk(chunk).forEach(item -> found.put(item.getMfn(), item.toTaxonomy())));
        return found;
    }

    private List<TaxonomyItem> loadChunk(List<String> mfns) {
        List<Object> keys = new ArrayList<>(mfns.size());
        mfns.forEach(mfn -> {
            TaxonomyItem key = new TaxonomyItem();
            key.setMfn(mfn);
            keys.add(key);
        });
        return dynamoDBMapper.batchLoad(keys).getOrDefault(TaxonomyItem.TABLE_NAME, List.of()).stream()
                .map(TaxonomyItem.class::cast)
                .toList();
    }

    public void saveAll(List<Taxonomy> taxonomies) {
        if (taxonomies.isEmpty()) {
            return;
        }
        List<DynamoDBMapper.FailedBatch> failures =
                dynamoDBMapper.batchSave(taxonomies.stream().map(TaxonomyItem::from).toList());
        if (!failures.isEmpty()) {
            throw new TaxonomyCatalogUnavailableException(
                    "Failed to write " + unprocessedCount(failures) + " taxonomy items",
                    failures.getFirst().getException());
        }
    }

    private static int unprocessedCount(List<DynamoDBMapper.FailedBatch> failures) {
        return failures.stream()
                .flatMap(failure -> failure.getUnprocessedItems().values().stream())
                .mapToInt(List::size)
                .sum();
    }

    public boolean updateCategoryIfAbsent(String mfn, String category, String categoryId) {
        Map<String, AttributeValue> values = new LinkedHashMap<>();
        values.put(":category", new AttributeValue(category));
        values.put(":categoryId", new AttributeValue(categoryId));
        values.put(":blank", new AttributeValue(""));
        try {
            amazonDynamoDB.updateItem(new UpdateItemRequest()
                    .withTableName(TaxonomyItem.TABLE_NAME)
                    .withKey(Map.of(TaxonomyItem.MFN, new AttributeValue(mfn)))
                    .withUpdateExpression("SET #category = :category, #categoryId = :categoryId")
                    .withConditionExpression(
                            "attribute_exists(#mfn) AND (attribute_not_exists(#category) OR #category = :blank)")
                    .withExpressionAttributeNames(Map.of(
                            "#mfn", TaxonomyItem.MFN,
                            "#category", TaxonomyItem.CATEGORY,
                            "#categoryId", TaxonomyItem.CATEGORY_ID))
                    .withExpressionAttributeValues(values));
            return true;
        } catch (ConditionalCheckFailedException e) {
            return false;
        }
    }

    public void forEachCategorized(Consumer<Taxonomy> consumer) {
        DynamoDBScanExpression expression = new DynamoDBScanExpression()
                .withFilterExpression("attribute_exists(#category) AND #category <> :blank")
                .withExpressionAttributeNames(Map.of("#category", TaxonomyItem.CATEGORY))
                .withExpressionAttributeValues(Map.of(":blank", new AttributeValue("")))
                .withLimit(SCAN_PAGE_SIZE);

        Map<String, AttributeValue> lastEvaluatedKey = null;
        do {
            expression.setExclusiveStartKey(lastEvaluatedKey);
            ScanResultPage<TaxonomyItem> page = dynamoDBMapper.scanPage(TaxonomyItem.class, expression);
            page.getResults().stream().map(TaxonomyItem::toTaxonomy).forEach(consumer);
            lastEvaluatedKey = page.getLastEvaluatedKey();
        } while (lastEvaluatedKey != null && !lastEvaluatedKey.isEmpty());
    }

    public long approximateSize() {
        return Objects.requireNonNullElse(
                amazonDynamoDB.describeTable(TaxonomyItem.TABLE_NAME).getTable().getItemCount(), 0L);
    }
}
