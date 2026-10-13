package pl.commercelink.migration;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.model.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.amazonaws.services.dynamodbv2.model.GlobalSecondaryIndexDescription;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import pl.commercelink.orders.rma.RMA;
import pl.commercelink.testsupport.DynamoDbLocalImage;
import pl.commercelink.orders.rma.RMAStatus;
import pl.commercelink.orders.rma.RmaListSortKey;

import java.time.Duration;
import java.util.List;
import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** V021 on an RMA table shaped like production: items without rmaListSortKey and no index. */
@Testcontainers(disabledWithoutDocker = true)
class V021AddRmaListSortKeyIndexDynamoDbIntegrationTest {

    @Container
    static final GenericContainer<?> DYNAMODB =
            new GenericContainer<>(DynamoDbLocalImage.IMAGE).withExposedPorts(8000);

    AmazonDynamoDB client;
    DynamoDBMapper mapper;

    @BeforeEach
    void createATableInTheProductionShape() {
        client = AmazonDynamoDBClientBuilder.standard()
                .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(
                        "http://" + DYNAMODB.getHost() + ":" + DYNAMODB.getMappedPort(8000), "eu-central-1"))
                .withCredentials(new AWSStaticCredentialsProvider(new BasicAWSCredentials("local", "local")))
                .build();
        mapper = new DynamoDBMapper(client);
        if (client.listTables().getTableNames().contains("RMA")) {
            client.deleteTable("RMA");
        }
        client.createTable(mapper.generateCreateTableRequest(RMA.class).withBillingMode(BillingMode.PAY_PER_REQUEST));
    }

    @AfterEach
    void letTheIndexBuildBeforeTheNextTestDropsTheTable() {
        awaitIndexesActive();
    }

    private RMA savedWithoutKey(String rmaId, RMAStatus status, LocalDateTime createdAt) {
        RMA rma = new RMA();
        rma.setStoreId("store-1");
        rma.setRmaId(rmaId);
        rma.setStatus(status);
        rma.setCreatedAt(createdAt);
        mapper.save(rma);
        client.updateItem(new UpdateItemRequest().withTableName("RMA")
                .withKey(Map.of("storeId", new AttributeValue("store-1"), "rmaId", new AttributeValue(rmaId)))
                .withUpdateExpression("REMOVE rmaListSortKey"));
        return mapper.load(RMA.class, "store-1", rmaId);
    }

    private Map<String, AttributeValue> item(String rmaId) {
        return client.getItem("RMA", Map.of("storeId", new AttributeValue("store-1"), "rmaId", new AttributeValue(rmaId))).getItem();
    }

    private V021_AddRmaListSortKeyIndex migration() {
        return new V021_AddRmaListSortKeyIndex(client, millis -> { }, Duration.ofSeconds(5));
    }

    @Test
    void everyRmaGetsItsKeyAndTheIndexIsCreatedWithAllAttributes() {
        // given
        RMA active = savedWithoutKey("a1", RMAStatus.WaitingForItems, LocalDateTime.of(2026, 10, 1, 10, 0));
        RMA closed = savedWithoutKey("c1", RMAStatus.Completed, LocalDateTime.of(2026, 9, 1, 10, 0));

        // when
        migration().execute();

        // then
        assertThat(item("a1").get("rmaListSortKey").getS()).isEqualTo(RmaListSortKey.of(active));
        assertThat(item("c1").get("rmaListSortKey").getS()).isEqualTo(RmaListSortKey.of(closed));
        assertThat(item("a1").get("version").getN()).isEqualTo(String.valueOf(active.getVersion()));
        GlobalSecondaryIndexDescription index = client.describeTable("RMA").getTable().getGlobalSecondaryIndexes().get(0);
        assertThat(index.getIndexName()).isEqualTo("StoreIdRmaListSortKeyIndex");
        assertThat(index.getProjection().getProjectionType()).isEqualTo("ALL");
        assertThat(index.getKeySchema()).extracting(KeySchemaElement::getAttributeName).containsExactly("storeId", "rmaListSortKey");
    }

    @Test
    void runningTwiceChangesNothing() {
        // given
        savedWithoutKey("a1", RMAStatus.New, LocalDateTime.of(2026, 10, 1, 10, 0));
        migration().execute();

        // when
        migration().execute();

        // then
        assertThat(client.describeTable("RMA").getTable().getGlobalSecondaryIndexes()).hasSize(1);
    }

    @Test
    void aSaveBetweenScanAndUpdateWins() {
        // given: the scan saw the RMA as New, an operator then completed it (version moved on, key written by the mapper)
        RMA scanned = savedWithoutKey("a1", RMAStatus.New, LocalDateTime.of(2026, 10, 1, 10, 0));
        RMA current = mapper.load(RMA.class, "store-1", "a1");
        current.setStatus(RMAStatus.Completed);
        mapper.save(current);

        // when
        migration().backfillRmaListSortKey(scanned, true);

        // then
        assertThat(item("a1").get("rmaListSortKey").getS()).startsWith("CLOSED#");
    }

    @Test
    void aProvisionedTableGetsAnIndexWithItsOwnThroughput() {
        // given
        client.deleteTable("RMA");
        client.createTable(mapper.generateCreateTableRequest(RMA.class)
                .withProvisionedThroughput(new ProvisionedThroughput(3L, 2L)));

        // when
        migration().execute();

        // then
        ProvisionedThroughputDescription capacity = client.describeTable("RMA").getTable()
                .getGlobalSecondaryIndexes().get(0).getProvisionedThroughput();
        assertThat(capacity.getReadCapacityUnits()).isEqualTo(3L);
        assertThat(capacity.getWriteCapacityUnits()).isEqualTo(2L);
    }

    @Test
    void aSecondRunRepairsWhatAnOldInstanceWroteAfterTheFirst() {
        // given: V021 already ran; an old instance then completed an RMA (stale ACTIVE# key) and created one without a key
        savedWithoutKey("a1", RMAStatus.New, LocalDateTime.of(2026, 10, 1, 10, 0));
        migration().execute();
        String versionBefore = item("a1").get("version").getN();
        client.updateItem(new UpdateItemRequest().withTableName("RMA")
                .withKey(Map.of("storeId", new AttributeValue("store-1"), "rmaId", new AttributeValue("a1")))
                .withUpdateExpression("SET #s = :s")
                .withExpressionAttributeNames(Map.of("#s", "status"))
                .withExpressionAttributeValues(Map.of(":s", new AttributeValue(RMAStatus.Completed.name()))));
        RMA fresh = savedWithoutKey("n1", RMAStatus.New, LocalDateTime.of(2026, 10, 2, 10, 0));
        String freshVersion = item("n1").get("version").getN();

        // when
        migration().execute();

        // then
        assertThat(item("a1").get("rmaListSortKey").getS()).startsWith("CLOSED#");
        assertThat(item("a1").get("version").getN()).isEqualTo(versionBefore);
        assertThat(item("n1").get("rmaListSortKey").getS()).isEqualTo(RmaListSortKey.of(fresh));
        assertThat(item("n1").get("version").getN()).isEqualTo(freshVersion);
    }

    @Test
    void anItemWithoutAVersionGetsItsKey() {
        // given
        client.putItem("RMA", Map.of("storeId", new AttributeValue("store-1"), "rmaId", new AttributeValue("u1"),
                "status", new AttributeValue(RMAStatus.New.name()),
                "createdAt", new AttributeValue("2026-10-01T10:00:00")));

        // when
        migration().execute();

        // then
        assertThat(item("u1").get("rmaListSortKey").getS()).startsWith("ACTIVE#");
        assertThat(item("u1")).doesNotContainKey("version");
    }

    @Test
    void aMalformedItemIsSkippedAndTheOthersStillGetTheirKeys() {
        // given
        savedWithoutKey("a1", RMAStatus.New, LocalDateTime.of(2026, 10, 1, 10, 0));
        client.putItem("RMA", Map.of("storeId", new AttributeValue("store-1"), "rmaId", new AttributeValue("bad"),
                "status", new AttributeValue("Bogus"),
                "createdAt", new AttributeValue("2026-10-01T10:00:00")));
        client.putItem("RMA", Map.of("storeId", new AttributeValue("store-1"), "rmaId", new AttributeValue("badDate"),
                "status", new AttributeValue(RMAStatus.New.name()),
                "createdAt", new AttributeValue("not-a-date")));
        savedWithoutKey("c1", RMAStatus.Completed, LocalDateTime.of(2026, 9, 1, 10, 0));

        // when
        migration().execute();

        // then
        assertThat(item("a1").get("rmaListSortKey").getS()).startsWith("ACTIVE#");
        assertThat(item("c1").get("rmaListSortKey").getS()).startsWith("CLOSED#");
        assertThat(item("bad")).doesNotContainKey("rmaListSortKey");
        assertThat(item("badDate")).doesNotContainKey("rmaListSortKey");
    }

    // V021 creates the list index without waiting for it; until dynamodb-local has built it a query can read nothing and
    // deleting the table under it can fail with InternalFailure
    private void awaitIndexesActive() {
        long deadline = System.currentTimeMillis() + Duration.ofMinutes(1).toMillis();
        while (!indexesActive()) {
            if (System.currentTimeMillis() > deadline) {
                throw new IllegalStateException("RMA indexes still building: "
                        + client.describeTable("RMA").getTable().getGlobalSecondaryIndexes());
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
    }

    // dynamodb-local reports no status at all for an index that is ready
    private boolean indexesActive() {
        List<GlobalSecondaryIndexDescription> indexes = client.describeTable("RMA").getTable().getGlobalSecondaryIndexes();
        return indexes == null || indexes.stream()
                .allMatch(index -> index.getIndexStatus() == null || "ACTIVE".equals(index.getIndexStatus()));
    }
}
