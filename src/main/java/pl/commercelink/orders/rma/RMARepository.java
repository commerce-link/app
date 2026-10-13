package pl.commercelink.orders.rma;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBScanExpression;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import org.springframework.stereotype.Repository;
import com.amazonaws.AmazonClientException;
import com.amazonaws.services.dynamodbv2.model.GlobalSecondaryIndexDescription;
import com.amazonaws.services.dynamodbv2.model.QueryRequest;
import com.amazonaws.services.dynamodbv2.model.QueryResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import pl.commercelink.migration.V021_AddRmaListSortKeyIndex;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.function.LongSupplier;
import pl.commercelink.starter.dynamodb.DynamoDbRepository;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Collection;
import java.util.Map;

@Slf4j
@Repository
public class RMARepository extends DynamoDbRepository<RMA>  {

    public static final String LIST_INDEX = V021_AddRmaListSortKeyIndex.INDEX;
    private static final int LIST_PAGE = 100;
    private static final long INDEX_CHECK_MILLIS = 60_000;

    private final LongSupplier clockMillis;
    private volatile boolean listIndexActive;
    private volatile long listIndexCheckedAt = Long.MIN_VALUE / 2;

    @Autowired
    public RMARepository(AmazonDynamoDB amazonDynamoDB) {
        this(amazonDynamoDB, System::currentTimeMillis);
    }

    RMARepository(AmazonDynamoDB amazonDynamoDB, LongSupplier clockMillis) {
        super(amazonDynamoDB);
        this.clockMillis = clockMillis;
    }

    /**
     * Whether StoreIdRmaListSortKeyIndex can serve the list. V021 creates it at start-up without waiting for the build,
     * so it is asked at most once a minute until it is ACTIVE and never again after; an unreadable status (e.g. a
     * missing DescribeTable permission) keeps the partition read, which is correct, only slower.
     */
    public boolean isListIndexActive() {
        if (listIndexActive) {
            return true;
        }
        long now = clockMillis.getAsLong();
        if (now - listIndexCheckedAt < INDEX_CHECK_MILLIS) {
            return false;
        }
        listIndexCheckedAt = now;
        try {
            List<GlobalSecondaryIndexDescription> indexes = amazonDynamoDB.describeTable("RMA").getTable().getGlobalSecondaryIndexes();
            listIndexActive = indexes != null && indexes.stream().anyMatch(index -> LIST_INDEX.equals(index.getIndexName())
                    && (index.getIndexStatus() == null || "ACTIVE".equals(index.getIndexStatus())));
        } catch (AmazonClientException e) {
            log.warn("RMA list index status unavailable, the list reads the store partition: {}", e.getMessage());
        }
        return listIndexActive;
    }

    /**
     * The store's RMAs whose list key lies in [lo, hi] (both inclusive), in key order or the reverse, read lazily 100 at
     * a time. From StoreIdRmaListSortKeyIndex once it is ACTIVE, before that from the store's partition sorted in memory,
     * so the list keeps working while V021 builds the index. An index entry whose stored key differs from the key of the
     * RMA it holds is dropped (same guard as DeliveriesRepository.readList): an older app version after a rollback saves
     * RMAs without maintaining the key.
     */
    public Iterator<RMA> listRange(String storeId, String lo, String hi, boolean newestFirst) {
        if (lo.compareTo(hi) > 0) {
            return Collections.emptyIterator();
        }
        return isListIndexActive() ? indexRange(storeId, lo, hi, newestFirst) : partitionRange(storeId, lo, hi, newestFirst);
    }

    private Iterator<RMA> indexRange(String storeId, String lo, String hi, boolean newestFirst) {
        Map<String, AttributeValue> values = Map.of(":storeId", new AttributeValue(storeId),
                ":lo", new AttributeValue(lo), ":hi", new AttributeValue(hi));
        return new Iterator<>() {
            private final Deque<RMA> buffer = new ArrayDeque<>();
            private Map<String, AttributeValue> startKey;
            private boolean exhausted;

            @Override
            public boolean hasNext() {
                while (buffer.isEmpty() && !exhausted) {
                    QueryResult page = amazonDynamoDB.query(new QueryRequest()
                            .withTableName("RMA")
                            .withIndexName(LIST_INDEX)
                            .withKeyConditionExpression("storeId = :storeId AND rmaListSortKey BETWEEN :lo AND :hi")
                            .withExpressionAttributeValues(values)
                            .withScanIndexForward(!newestFirst)
                            .withLimit(LIST_PAGE)
                            .withExclusiveStartKey(startKey));
                    for (Map<String, AttributeValue> item : page.getItems()) {
                        RMA rma = dynamoDBMapper.marshallIntoObject(RMA.class, item);
                        AttributeValue stored = item.get("rmaListSortKey");
                        if (stored != null && stored.getS().equals(RmaListSortKey.of(rma))) {
                            buffer.add(rma);
                        }
                    }
                    startKey = page.getLastEvaluatedKey();
                    exhausted = startKey == null || startKey.isEmpty();
                }
                return !buffer.isEmpty();
            }

            @Override
            public RMA next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                return buffer.poll();
            }
        };
    }

    private Iterator<RMA> partitionRange(String storeId, String lo, String hi, boolean newestFirst) {
        Comparator<RMA> byKey = Comparator.comparing(RmaListSortKey::of);
        return findAllByStoreId(storeId).stream()
                .filter(rma -> {
                    String key = RmaListSortKey.of(rma);
                    return key.compareTo(lo) >= 0 && key.compareTo(hi) <= 0;
                })
                .sorted(newestFirst ? byKey.reversed() : byKey)
                .iterator();
    }

    public RMA findById(String storeId, String rmaId) {
        return dynamoDBMapper.load(RMA.class, storeId, rmaId);
    }

    /** The store's RMAs with these ids, read in batches of 100 keys; ids not in the store are absent. */
    public List<RMA> findAllByIds(String storeId, Collection<String> rmaIds) {
        List<RMA> keys = rmaIds.stream().distinct().map(id -> {
            RMA key = new RMA();
            key.setStoreId(storeId);
            key.setRmaId(id);
            return key;
        }).toList();
        if (keys.isEmpty()) {
            return List.of();
        }
        return dynamoDBMapper.batchLoad(keys).values().stream().flatMap(List::stream).map(RMA.class::cast).toList();
    }

    /** Every RMA of the store, whatever its status: a query of the store's partition. */
    public List<RMA> findAllByStoreId(String storeId) {
        RMA key = new RMA();
        key.setStoreId(storeId);
        return dynamoDBMapper.query(RMA.class, new DynamoDBQueryExpression<RMA>().withHashKeyValues(key));
    }

    public List<RMA> findAll() {
        DynamoDBScanExpression scanExpression = new DynamoDBScanExpression();
        return dynamoDBMapper.scan(RMA.class, scanExpression);
    }

    public List<RMA> findAllByStoreIdAndStatus(String storeId, RMAStatus... statuses) {
        Map<String, AttributeValue> eav = new HashMap<>();
        eav.put(":storeId", new AttributeValue().withS(storeId));

        StringBuilder filterExpression = new StringBuilder("storeId = :storeId AND (");
        for (int i = 0; i < statuses.length; i++) {
            if (i > 0) {
                filterExpression.append(" OR ");
            }
            filterExpression.append("#status").append(i).append(" = :status").append(i);
            eav.put(":status" + i, new AttributeValue().withS(statuses[i].name()));
        }
        filterExpression.append(")");

        Map<String, String> expressionAttributeNames = new HashMap<>();
        for (int i = 0; i < statuses.length; i++) {
            expressionAttributeNames.put("#status" + i, "status");
        }

        DynamoDBScanExpression scanExpression = new DynamoDBScanExpression()
                .withFilterExpression(filterExpression.toString())
                .withExpressionAttributeValues(eav)
                .withExpressionAttributeNames(expressionAttributeNames);

        return dynamoDBMapper.scan(RMA.class, scanExpression);
    }

    public RMA findByExternalReturnId(String storeId, String marketplace, String externalReturnId) {
        Map<String, AttributeValue> eav = new HashMap<>();
        eav.put(":storeId", new AttributeValue().withS(storeId));
        eav.put(":marketplace", new AttributeValue().withS(marketplace));
        eav.put(":externalReturnId", new AttributeValue().withS(externalReturnId));
        DynamoDBQueryExpression<RMA> query = new DynamoDBQueryExpression<RMA>()
                .withKeyConditionExpression("storeId = :storeId")
                .withFilterExpression("externalReturnId = :externalReturnId AND marketplace = :marketplace")
                .withExpressionAttributeValues(eav);
        List<RMA> found = dynamoDBMapper.query(RMA.class, query);
        return found.isEmpty() ? null : found.get(0);
    }
}
