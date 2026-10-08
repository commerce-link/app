package pl.commercelink.inventory.deliveries;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.BillingMode;
import com.amazonaws.services.dynamodbv2.model.UpdateItemRequest;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import pl.commercelink.migration.V019_AddDeliveriesListKeyIndex;
import pl.commercelink.migration.V020_RenameDeliveriesListSortKey;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DeliveriesListIndexDynamoDbIntegrationTest {

    @Container
    static final GenericContainer<?> DYNAMODB =
            new GenericContainer<>(DockerImageName.parse("amazon/dynamodb-local:latest")).withExposedPorts(8000);

    static AmazonDynamoDB client;
    static DynamoDBMapper mapper;
    static DeliveriesRepository deliveries;

    @BeforeAll
    static void createTheDeliveriesTableAndMigrateIt() {
        client = AmazonDynamoDBClientBuilder.standard()
                .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(
                        "http://" + DYNAMODB.getHost() + ":" + DYNAMODB.getMappedPort(8000), "eu-central-1"))
                .withCredentials(new AWSStaticCredentialsProvider(new BasicAWSCredentials("local", "local")))
                .build();
        mapper = new DynamoDBMapper(client);
        client.createTable(mapper.generateCreateTableRequest(Delivery.class).withBillingMode(BillingMode.PAY_PER_REQUEST));
        deliveries = new DeliveriesRepository(client);
        // before V019: these rows were written by the old code and carry no key at all
        saveWithoutKey(inTransit("store-1", "aaaa0001", LocalDate.of(2026, 10, 1)));
        saveWithoutKey(inTransit("store-1", "aaaa0002", null));
        saveWithoutKey(received("store-1", "bbbb0001", LocalDateTime.of(2026, 9, 20, 9, 0), false));
        saveWithoutKey(received("store-1", "bbbb0002", LocalDateTime.of(2026, 5, 2, 9, 0), true));
        saveWithoutKey(inTransit("store-2", "cccc0001", LocalDate.of(2026, 10, 2)));
        // the order of a real deployment: V019 (old index and listKey), then V020 replaces both
        new V019_AddDeliveriesListKeyIndex(client).execute();
        new V020_RenameDeliveriesListSortKey(client).execute();
    }

    static Delivery inTransit(String storeId, String id, LocalDate planned) {
        Delivery delivery = new Delivery();
        delivery.setStoreId(storeId);
        delivery.setDeliveryId(id + "-0000-0000-0000-000000000000");
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

    static void saveWithoutKey(Delivery delivery) {
        mapper.save(delivery);
        client.updateItem(new UpdateItemRequest().withTableName("Deliveries")
                .withKey(Map.of("storeId", new AttributeValue(delivery.getStoreId()),
                        "deliveryId", new AttributeValue(delivery.getDeliveryId())))
                .withUpdateExpression("REMOVE deliveryListSortKey"));
    }

    @Test
    @Order(1)
    void afterTheMigrationsEachPartOfTheListIsReadFromTheIndex() {
        // then
        assertThat(deliveries.findInTransit("store-1")).hasSize(2);
        assertThat(deliveries.findInTransit("store-2")).hasSize(1);
        assertThat(deliveries.findToSettle("store-1")).extracting(Delivery::getDeliveryId)
                .containsExactly("bbbb0001-0000-0000-0000-000000000000");
        assertThat(deliveries.findReceivedSince("store-1", LocalDate.of(2026, 9, 20)))
                .extracting(Delivery::getDeliveryId).containsExactly("bbbb0001-0000-0000-0000-000000000000");
        assertThat(deliveries.findReceivedSince("store-1", LocalDate.of(2026, 9, 21))).isEmpty();
        assertThat(deliveries.findReceivedSince("store-1", null)).hasSize(2);
        assertThat(deliveries.findByDeliveryIdPrefix("store-1", "bbbb0002")).extracting(Delivery::getDeliveryId)
                .containsExactly("bbbb0002-0000-0000-0000-000000000000");
        // COUNT on the active index
        assertThat(deliveries.countToSettle("store-1")).isEqualTo(1);
        assertThat(deliveries.countToSettle("store-2")).isZero();
    }

    @Test
    @Order(2)
    void theLastMigrationRunsTwiceAndASavedDeliveryMovesByItself() {
        // given
        new V020_RenameDeliveriesListSortKey(client).execute();
        Delivery arriving = mapper.load(Delivery.class, "store-1", "aaaa0001-0000-0000-0000-000000000000");

        // when
        arriving.setReceivedAt(LocalDateTime.of(2026, 9, 30, 12, 0));
        mapper.save(arriving);

        // then
        assertThat(deliveries.findInTransit("store-1")).extracting(Delivery::getDeliveryId)
                .containsExactly("aaaa0002-0000-0000-0000-000000000000");
        assertThat(deliveries.findToSettle("store-1")).hasSize(2);
        assertThat(deliveries.countToSettle("store-1")).isEqualTo(2);
    }

    @Test
    @Order(3)
    void staleIndexEntryIsDroppedByItsRecomputedKey() {
        // given: the index still says "on its way" while the delivery was already received
        client.updateItem(new UpdateItemRequest().withTableName("Deliveries")
                .withKey(Map.of("storeId", new AttributeValue("store-1"),
                        "deliveryId", new AttributeValue("aaaa0002-0000-0000-0000-000000000000")))
                .withUpdateExpression("SET receivedAt = :r")
                .withExpressionAttributeValues(Map.of(":r", new AttributeValue("2026-09-30T13:00:00"))));

        // when / then
        assertThat(deliveries.findInTransit("store-1")).isEmpty();
    }

    @Test
    @Order(4)
    void backfillDoesNotOverwriteAKeyWrittenByASaveAfterTheScan() {
        // given: a scanned copy that is stale, because the delivery was saved (fresh key, new version) after the scan
        Delivery scanned = mapper.load(Delivery.class, "store-2", "cccc0001-0000-0000-0000-000000000000");
        Delivery saved = mapper.load(Delivery.class, "store-2", "cccc0001-0000-0000-0000-000000000000");
        saved.setReceivedAt(LocalDateTime.of(2026, 9, 30, 14, 0));
        mapper.save(saved);
        String freshKey = DeliveryListKey.of(saved);

        // when
        new V020_RenameDeliveriesListSortKey(client).backfillDeliveryListSortKey(scanned, true);

        // then
        assertThat(rawItem("store-2", "cccc0001-0000-0000-0000-000000000000").get("deliveryListSortKey").getS()).isEqualTo(freshKey);
    }

    @Test
    @Order(5)
    void backfillLeavesTheVersionUntouched() {
        // given
        Map<String, AttributeValue> before = rawItem("store-1", "bbbb0001-0000-0000-0000-000000000000");
        new V020_RenameDeliveriesListSortKey(client).backfillDeliveryListSortKey(
                mapper.load(Delivery.class, "store-1", "bbbb0001-0000-0000-0000-000000000000"), true);

        // when
        new V020_RenameDeliveriesListSortKey(client).execute();

        // then
        assertThat(rawItem("store-1", "bbbb0001-0000-0000-0000-000000000000").get("version")).isEqualTo(before.get("version"));
    }

    @Test
    @Order(6)
    void aBlankDeliveryNumberFindsNothing() {
        assertThat(deliveries.findByDeliveryIdPrefix("store-1", " ")).isEmpty();
    }

    static Map<String, AttributeValue> rawItem(String storeId, String deliveryId) {
        return client.getItem("Deliveries", Map.of("storeId", new AttributeValue(storeId),
                "deliveryId", new AttributeValue(deliveryId))).getItem();
    }
}
