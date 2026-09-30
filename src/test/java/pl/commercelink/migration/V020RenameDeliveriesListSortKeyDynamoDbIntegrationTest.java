package pl.commercelink.migration;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import pl.commercelink.inventory.deliveries.DeliveriesRepository;
import pl.commercelink.inventory.deliveries.Delivery;
import pl.commercelink.inventory.deliveries.DeliveryListSortKey;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V020 on a Deliveries table shaped like production after V019: the old GSI and listKey values in the old prefix format.
 */
@Testcontainers(disabledWithoutDocker = true)
class V020RenameDeliveriesListSortKeyDynamoDbIntegrationTest {

    @Container
    static final GenericContainer<?> DYNAMODB =
            new GenericContainer<>(DockerImageName.parse("amazon/dynamodb-local:latest")).withExposedPorts(8000);

    static final String TRANSIT_DATED = "aaaa0001-0000-0000-0000-000000000000";
    static final String TRANSIT_UNDATED = "aaaa0002-0000-0000-0000-000000000000";
    static final String TO_SETTLE = "bbbb0001-0000-0000-0000-000000000000";
    static final String SETTLED = "bbbb0002-0000-0000-0000-000000000000";
    static final String OTHER_STORE = "cccc0001-0000-0000-0000-000000000000";

    AmazonDynamoDB client;
    DynamoDBMapper mapper;
    DeliveriesRepository deliveries;

    @BeforeEach
    void createATableInTheProductionShape() {
        client = AmazonDynamoDBClientBuilder.standard()
                .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(
                        "http://" + DYNAMODB.getHost() + ":" + DYNAMODB.getMappedPort(8000), "eu-central-1"))
                .withCredentials(new AWSStaticCredentialsProvider(new BasicAWSCredentials("local", "local")))
                .build();
        mapper = new DynamoDBMapper(client);
        deliveries = new DeliveriesRepository(client);
        dropTableIfPresent();
        createTable(new CreateTableRequest().withBillingMode(BillingMode.PAY_PER_REQUEST));
        createOldIndex(null);
        saveInOldFormat(inTransit("store-1", TRANSIT_DATED, LocalDate.of(2026, 10, 1)), "IN_TRANSIT_OLD");
        saveInOldFormat(inTransit("store-1", TRANSIT_UNDATED, null), "IN_TRANSIT_OLD");
        saveInOldFormat(received("store-1", TO_SETTLE, LocalDateTime.of(2026, 9, 20, 9, 0), false), "TO_SETTLE_OLD");
        saveInOldFormat(received("store-1", SETTLED, LocalDateTime.of(2026, 5, 2, 9, 0), true), "SETTLED_OLD");
        saveInOldFormat(inTransit("store-2", OTHER_STORE, LocalDate.of(2026, 10, 2)), "IN_TRANSIT_OLD");
    }

    @Test
    void renamesTheIndexAndTheAttributeAndKeepsTheVersion() {
        // given
        Map<String, AttributeValue> versionBefore = rawItem("store-1", TO_SETTLE);

        // when
        migration().execute();

        // then
        assertThat(indexes()).extracting(GlobalSecondaryIndexDescription::getIndexName)
                .containsExactly(V020_RenameDeliveriesListSortKey.NEW_INDEX);
        GlobalSecondaryIndexDescription index = indexes().get(0);
        assertThat(index.getProjection().getProjectionType()).isEqualTo(ProjectionType.INCLUDE.toString());
        assertThat(index.getProjection().getNonKeyAttributes()).contains("tracking", "receivedAt");
        assertThat(key("store-1", TRANSIT_DATED)).isEqualTo("IN_TRANSIT#2026-10-01");
        assertThat(key("store-1", TRANSIT_UNDATED)).isEqualTo("IN_TRANSIT#9999-12-31");
        assertThat(key("store-1", TO_SETTLE)).isEqualTo("TO_SETTLE#2026-09-20T09:00");
        assertThat(key("store-1", SETTLED)).isEqualTo("SETTLED#2026-05-02T09:00");
        assertThat(key("store-2", OTHER_STORE)).isEqualTo("IN_TRANSIT#2026-10-02");
        for (Map.Entry<String, String> item : Map.of("store-1", TO_SETTLE, "store-2", OTHER_STORE).entrySet()) {
            assertThat(rawItem(item.getKey(), item.getValue())).doesNotContainKey("listKey");
        }
        assertThat(rawItem("store-1", TRANSIT_DATED)).doesNotContainKey("listKey");
        assertThat(rawItem("store-1", TRANSIT_UNDATED)).doesNotContainKey("listKey");
        assertThat(rawItem("store-1", SETTLED)).doesNotContainKey("listKey");
        assertThat(rawItem("store-1", TO_SETTLE).get("version")).isEqualTo(versionBefore.get("version"));
    }

    @Test
    void theListReadsTheNewIndex() {
        // when
        migration().execute();

        // then
        assertThat(deliveries.findInTransit("store-1")).extracting(Delivery::getDeliveryId)
                .containsExactlyInAnyOrder(TRANSIT_DATED, TRANSIT_UNDATED);
        assertThat(deliveries.findInTransit("store-2")).hasSize(1);
        assertThat(deliveries.findToSettle("store-1")).extracting(Delivery::getDeliveryId).containsExactly(TO_SETTLE);
        assertThat(deliveries.countToSettle("store-1")).isEqualTo(1);
        assertThat(deliveries.countToSettle("store-2")).isZero();
        assertThat(deliveries.findReceivedBetween("store-1", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)))
                .extracting(Delivery::getDeliveryId).containsExactly(TO_SETTLE);
        assertThat(deliveries.findReceivedBetween("store-1", null, null)).hasSize(2);
    }

    @Test
    void aSecondRunChangesNothing() {
        // given
        migration().execute();
        Map<String, Map<String, AttributeValue>> before = Map.of(
                TRANSIT_DATED, rawItem("store-1", TRANSIT_DATED), TO_SETTLE, rawItem("store-1", TO_SETTLE));
        TableDescription tableBefore = client.describeTable("Deliveries").getTable();

        // when
        migration().execute();

        // then
        assertThat(rawItem("store-1", TRANSIT_DATED)).isEqualTo(before.get(TRANSIT_DATED));
        assertThat(rawItem("store-1", TO_SETTLE)).isEqualTo(before.get(TO_SETTLE));
        assertThat(client.describeTable("Deliveries").getTable().getGlobalSecondaryIndexes())
                .hasSameSizeAs(tableBefore.getGlobalSecondaryIndexes());
    }

    @Test
    void aRunThatStoppedAfterDroppingTheOldIndexResumes() {
        // given: the previous start-up deleted the old index and was stopped before the backfill
        client.updateTable(new UpdateTableRequest().withTableName("Deliveries").withGlobalSecondaryIndexUpdates(
                new GlobalSecondaryIndexUpdate().withDelete(
                        new DeleteGlobalSecondaryIndexAction().withIndexName(V019_AddDeliveriesListKeyIndex.INDEX))));

        // when
        migration().execute();

        // then
        assertThat(indexes()).extracting(GlobalSecondaryIndexDescription::getIndexName)
                .containsExactly(V020_RenameDeliveriesListSortKey.NEW_INDEX);
        assertThat(key("store-1", TO_SETTLE)).isEqualTo("TO_SETTLE#2026-09-20T09:00");
    }

    @Test
    void backfillDoesNotOverwriteAKeyWrittenByASaveAfterTheScan() {
        // given: a scanned copy that is stale, because the delivery was saved (fresh key, new version) after the scan
        Delivery scanned = mapper.load(Delivery.class, "store-2", OTHER_STORE);
        Delivery saved = mapper.load(Delivery.class, "store-2", OTHER_STORE);
        saved.setReceivedAt(LocalDateTime.of(2026, 9, 30, 14, 0));
        mapper.save(saved);
        String freshKey = DeliveryListSortKey.of(saved);

        // when
        migration().backfillDeliveryListSortKey(scanned, true);

        // then
        assertThat(key("store-2", OTHER_STORE)).isEqualTo(freshKey);
        assertThat(rawItem("store-2", OTHER_STORE).get("version").getN()).isEqualTo(String.valueOf(saved.getVersion()));
    }

    @Test
    void theNewIndexOfAProvisionedTableTakesTheTablesThroughput() {
        // given
        dropTableIfPresent();
        createTable(new CreateTableRequest().withProvisionedThroughput(new ProvisionedThroughput(7L, 3L)));
        createOldIndex(new ProvisionedThroughput(7L, 3L));

        // when
        migration().execute();

        // then
        GlobalSecondaryIndexDescription index = indexes().get(0);
        assertThat(index.getIndexName()).isEqualTo(V020_RenameDeliveriesListSortKey.NEW_INDEX);
        assertThat(index.getProvisionedThroughput().getReadCapacityUnits()).isEqualTo(7L);
        assertThat(index.getProvisionedThroughput().getWriteCapacityUnits()).isEqualTo(3L);
    }

    @Test
    void theOldKeyIsAlsoRemovedFromAnItemWithoutAVersion() {
        // given
        client.updateItem(new UpdateItemRequest().withTableName("Deliveries")
                .withKey(Map.of("storeId", new AttributeValue("store-1"), "deliveryId", new AttributeValue(TRANSIT_DATED)))
                .withUpdateExpression("REMOVE version"));

        // when
        migration().execute();

        // then
        assertThat(rawItem("store-1", TRANSIT_DATED)).doesNotContainKey("listKey").doesNotContainKey("version");
        assertThat(key("store-1", TRANSIT_DATED)).isEqualTo("IN_TRANSIT#2026-10-01");
    }

    private V020_RenameDeliveriesListSortKey migration() {
        // dynamodb-local reports CREATING and DELETING for a moment like DynamoDB does, so the waits really wait here,
        // only with a shorter pause than the 5 s of production
        return new V020_RenameDeliveriesListSortKey(client, millis -> sleep(100), Duration.ofMinutes(10));
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private void dropTableIfPresent() {
        try {
            client.deleteTable("Deliveries");
        } catch (ResourceNotFoundException absent) {
            // first test of the class
        }
    }

    private void createTable(CreateTableRequest settings) {
        CreateTableRequest request = mapper.generateCreateTableRequest(Delivery.class);
        request.setBillingMode(settings.getBillingMode());
        request.setProvisionedThroughput(settings.getProvisionedThroughput());
        client.createTable(request);
    }

    private void createOldIndex(ProvisionedThroughput throughput) {
        CreateGlobalSecondaryIndexAction create = new CreateGlobalSecondaryIndexAction()
                .withIndexName(V019_AddDeliveriesListKeyIndex.INDEX)
                .withKeySchema(new KeySchemaElement("storeId", KeyType.HASH), new KeySchemaElement("listKey", KeyType.RANGE))
                .withProjection(new Projection().withProjectionType(ProjectionType.INCLUDE)
                        .withNonKeyAttributes(V019_AddDeliveriesListKeyIndex.PROJECTED));
        if (throughput != null) {
            create.withProvisionedThroughput(throughput);
        }
        client.updateTable(new UpdateTableRequest().withTableName("Deliveries")
                .withAttributeDefinitions(new AttributeDefinition("storeId", ScalarAttributeType.S),
                        new AttributeDefinition("listKey", ScalarAttributeType.S))
                .withGlobalSecondaryIndexUpdates(new GlobalSecondaryIndexUpdate().withCreate(create)));
    }

    /** Writes the delivery through the mapper (so it has a version), then turns its key into what V019 left in production. */
    private void saveInOldFormat(Delivery delivery, String kind) {
        mapper.save(delivery);
        String oldKey = switch (kind) {
            case "IN_TRANSIT_OLD" -> "T#" + (delivery.getEstimatedDeliveryAt() == null ? "9999-12-31" : delivery.getEstimatedDeliveryAt());
            case "TO_SETTLE_OLD" -> "S#" + delivery.getReceivedAt();
            default -> "R#" + delivery.getReceivedAt();
        };
        client.updateItem(new UpdateItemRequest().withTableName("Deliveries")
                .withKey(Map.of("storeId", new AttributeValue(delivery.getStoreId()),
                        "deliveryId", new AttributeValue(delivery.getDeliveryId())))
                .withUpdateExpression("SET listKey = :k REMOVE deliveryListSortKey")
                .withExpressionAttributeValues(Map.of(":k", new AttributeValue(oldKey))));
    }

    private List<GlobalSecondaryIndexDescription> indexes() {
        return client.describeTable("Deliveries").getTable().getGlobalSecondaryIndexes();
    }

    private String key(String storeId, String deliveryId) {
        return rawItem(storeId, deliveryId).get("deliveryListSortKey").getS();
    }

    private Map<String, AttributeValue> rawItem(String storeId, String deliveryId) {
        return client.getItem("Deliveries", Map.of("storeId", new AttributeValue(storeId),
                "deliveryId", new AttributeValue(deliveryId))).getItem();
    }

    static Delivery inTransit(String storeId, String id, LocalDate planned) {
        Delivery delivery = new Delivery();
        delivery.setStoreId(storeId);
        delivery.setDeliveryId(id);
        delivery.setProvider("Acme");
        delivery.setEstimatedDeliveryAt(planned);
        delivery.setOrderedAt(LocalDateTime.of(2026, 9, 25, 8, 0));
        return delivery;
    }

    static Delivery received(String storeId, String id, LocalDateTime at, boolean invoiced) {
        Delivery delivery = inTransit(storeId, id, LocalDate.of(2026, 9, 1));
        delivery.setReceivedAt(at);
        delivery.setInvoiced(invoiced);
        return delivery;
    }
}
