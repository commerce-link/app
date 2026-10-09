package pl.commercelink.orders;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.model.BillingMode;
import com.amazonaws.services.dynamodbv2.model.CreateTableRequest;
import com.amazonaws.services.dynamodbv2.model.Projection;
import com.amazonaws.services.dynamodbv2.model.ProjectionType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** The archive reads closed orders of one store from StoreIdOrderedAtIndex, newest first, page after page. */
@Testcontainers(disabledWithoutDocker = true)
class OrdersArchiveDynamoDbIntegrationTest {

    @Container
    static final GenericContainer<?> DYNAMODB =
            new GenericContainer<>(DockerImageName.parse("amazon/dynamodb-local:latest")).withExposedPorts(8000);

    static OrdersRepository orders;
    static final Set<OrderStatus> CLOSED = Set.of(OrderStatus.Completed, OrderStatus.Cancelled);

    @BeforeAll
    static void seed() {
        AmazonDynamoDB client = AmazonDynamoDBClientBuilder.standard()
                .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(
                        "http://" + DYNAMODB.getHost() + ":" + DYNAMODB.getMappedPort(8000), "eu-central-1"))
                .withCredentials(new AWSStaticCredentialsProvider(new BasicAWSCredentials("local", "local")))
                .build();
        CreateTableRequest table = new DynamoDBMapper(client).generateCreateTableRequest(Order.class)
                .withBillingMode(BillingMode.PAY_PER_REQUEST);
        // the mapper creates indexes with a keys-only projection; production projects these attributes (V001)
        table.getGlobalSecondaryIndexes().stream()
                .filter(index -> index.getIndexName().equals("StoreIdOrderedAtIndex"))
                .forEach(index -> index.setProjection(new Projection().withProjectionType(ProjectionType.INCLUDE)
                        .withNonKeyAttributes("status", "email", "fulfilmentType")));
        client.createTable(table);
        orders = new OrdersRepository(client);

        // 30 closed orders of store-a on 1–30 May at noon, one open order, one closed order of store-b
        for (int day = 1; day <= 30; day++) {
            save("store-a", day % 3 == 0 ? OrderStatus.Cancelled : OrderStatus.Completed,
                    LocalDateTime.of(2026, 5, day, 12, 0));
        }
        save("store-a", OrderStatus.Delivered, LocalDateTime.of(2026, 5, 15, 13, 0));
        save("store-a", OrderStatus.Completed, LocalDateTime.of(2026, 5, 31, 23, 59, 59, 900_000_000));
        save("store-b", OrderStatus.Completed, LocalDateTime.of(2026, 5, 20, 12, 0));
    }

    private static void save(String storeId, OrderStatus status, LocalDateTime orderedAt) {
        Order order = new Order(storeId);
        order.setStatus(status);
        order.setOrderedAt(orderedAt);
        orders.save(order);
    }

    private static List<ArchiveIndexEntry> all(Iterator<ArchiveIndexEntry> it) {
        List<ArchiveIndexEntry> out = new ArrayList<>();
        it.forEachRemaining(out::add);
        return out;
    }

    @Test
    void readsOnlyClosedOrdersOfTheStoreNewestFirst() {
        // when
        List<ArchiveIndexEntry> entries = all(orders.archiveIndex("store-a",
                new ArchiveIndexRequest(CLOSED, null, null, null, true, 100)));

        // then
        assertThat(entries).hasSize(31);
        assertThat(entries).extracting(ArchiveIndexEntry::status).doesNotContain(OrderStatus.Delivered);
        assertThat(entries.get(0).orderedAt()).startsWith("2026-05-31T23:59:59");
        assertThat(entries).extracting(ArchiveIndexEntry::orderedAt).isSortedAccordingTo(java.util.Comparator.reverseOrder());
    }

    @Test
    void endDayIsIncludedUpToTheLastFractionOfASecond() {
        // when
        List<ArchiveIndexEntry> entries = all(orders.archiveIndex("store-a",
                new ArchiveIndexRequest(CLOSED, LocalDate.of(2026, 5, 30), LocalDate.of(2026, 5, 31), null, true, 100)));

        // then
        assertThat(entries).extracting(ArchiveIndexEntry::orderedAt)
                .anyMatch(at -> at.startsWith("2026-05-31T23:59:59"))
                .anyMatch(at -> at.startsWith("2026-05-30T12:00"))
                .hasSize(2);
    }

    @Test
    void readsPastOneDynamoDbPage() {
        // when — 3 entries per DynamoDB call, so 31 entries take 11 calls
        List<ArchiveIndexEntry> entries = all(orders.archiveIndex("store-a",
                new ArchiveIndexRequest(CLOSED, null, null, null, true, 3)));

        // then
        assertThat(entries).hasSize(31);
    }

    @Test
    void cursorContinuesAfterTheEntryInBothDirections() {
        // given
        List<ArchiveIndexEntry> newest = all(orders.archiveIndex("store-a",
                new ArchiveIndexRequest(CLOSED, null, null, null, true, 100)));
        ArchiveCursor tenth = newest.get(9).cursor();

        // when
        Iterator<ArchiveIndexEntry> older = orders.archiveIndex("store-a", new ArchiveIndexRequest(CLOSED, null, null, tenth, true, 100));
        Iterator<ArchiveIndexEntry> newer = orders.archiveIndex("store-a", new ArchiveIndexRequest(CLOSED, null, null, tenth, false, 100));

        // then
        assertThat(older.next()).isEqualTo(newest.get(10));
        assertThat(newer.next()).isEqualTo(newest.get(8));
    }

    @Test
    void statusFilterKeepsOnlyTheAskedStatus() {
        // when
        List<ArchiveIndexEntry> cancelled = all(orders.archiveIndex("store-a",
                new ArchiveIndexRequest(Set.of(OrderStatus.Cancelled), null, null, null, true, 100)));

        // then
        assertThat(cancelled).hasSize(10).allMatch(e -> e.status() == OrderStatus.Cancelled);
    }

    @Test
    void cursorNeverLeavesTheStore() {
        // given — a cursor taken from store-a, used by store-b
        ArchiveCursor foreign = all(orders.archiveIndex("store-a",
                new ArchiveIndexRequest(CLOSED, null, null, null, true, 100))).get(0).cursor();

        // when
        List<ArchiveIndexEntry> entries = all(orders.archiveIndex("store-b",
                new ArchiveIndexRequest(CLOSED, null, null, foreign, true, 100)));

        // then
        assertThat(entries).hasSize(1).allMatch(e -> e.orderedAt().startsWith("2026-05-20"));
    }

    @Test
    void fromOnlyAndToOnlyBoundsEachCutOneSide() {
        // when
        List<ArchiveIndexEntry> since = all(orders.archiveIndex("store-a",
                new ArchiveIndexRequest(CLOSED, LocalDate.of(2026, 5, 29), null, null, true, 100)));
        List<ArchiveIndexEntry> until = all(orders.archiveIndex("store-a",
                new ArchiveIndexRequest(CLOSED, null, LocalDate.of(2026, 5, 2), null, true, 100)));

        // then
        assertThat(since).extracting(ArchiveIndexEntry::orderedAt).allMatch(at -> at.compareTo("2026-05-29") >= 0).hasSize(3);
        assertThat(until).extracting(ArchiveIndexEntry::orderedAt).allMatch(at -> at.compareTo("2026-05-03") < 0).hasSize(2);
    }
}
