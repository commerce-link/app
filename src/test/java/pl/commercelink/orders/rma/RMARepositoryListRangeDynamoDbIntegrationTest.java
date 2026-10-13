package pl.commercelink.orders.rma;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.BillingMode;
import com.amazonaws.services.dynamodbv2.model.UpdateItemRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import pl.commercelink.migration.V021_AddRmaListSortKeyIndex;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.commercelink.orders.rma.RmaListSortKey.*;

/** The list reads key ranges of StoreIdRmaListSortKeyIndex, newest first or oldest first, every page followed. */
@Testcontainers(disabledWithoutDocker = true)
class RMARepositoryListRangeDynamoDbIntegrationTest {

    @Container
    static final GenericContainer<?> DYNAMODB =
            new GenericContainer<>(DockerImageName.parse("amazon/dynamodb-local:latest")).withExposedPorts(8000);

    AmazonDynamoDB client;
    DynamoDBMapper mapper;
    RMARepository rmas;

    @BeforeEach
    void seed() {
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
        rmas = new RMARepository(client);
        // 150 closed RMAs of store-1, one a day back from 13.10.2026 (more than one 100-item page), 3 active, 1 of store-2
        for (int i = 0; i < 150; i++) {
            save("store-1", "c" + i, RMAStatus.Completed, LocalDateTime.of(2026, 10, 13, 12, 0).minusDays(i));
        }
        save("store-1", "a1", RMAStatus.New, LocalDateTime.of(2026, 10, 12, 8, 0));
        save("store-1", "a2", RMAStatus.ItemsReceived, LocalDateTime.of(2026, 10, 13, 23, 59, 59));
        save("store-1", "a3", RMAStatus.Processing, LocalDateTime.of(2026, 9, 1, 8, 0));
        save("store-2", "x1", RMAStatus.New, LocalDateTime.of(2026, 10, 12, 9, 0));
    }

    private void save(String storeId, String rmaId, RMAStatus status, LocalDateTime createdAt) {
        RMA rma = new RMA();
        rma.setStoreId(storeId);
        rma.setRmaId(rmaId);
        rma.setStatus(status);
        rma.setCreatedAt(createdAt);
        rmas.save(rma);
    }

    private static List<String> ids(Iterator<RMA> it) {
        List<String> out = new ArrayList<>();
        it.forEachRemaining(r -> out.add(r.getRmaId()));
        return out;
    }

    private void buildIndex() {
        new V021_AddRmaListSortKeyIndex(client).execute();
    }

    @Test
    void activeRangeIsNewestFirstAndOnlyThisStore() {
        // given
        buildIndex();

        // when
        List<String> active = ids(rmas.listRange("store-1", ACTIVE, upperBound(ACTIVE, null), true));

        // then
        assertThat(active).containsExactly("a2", "a1", "a3");
    }

    @Test
    void closedRangeFollowsEveryPageAndTheEndDayIsIncluded() {
        // given
        buildIndex();

        // when
        List<String> all = ids(rmas.listRange("store-1", CLOSED, upperBound(CLOSED, null), true));
        List<String> window = ids(rmas.listRange("store-1", lowerBound(CLOSED, LocalDate.of(2026, 10, 11)),
                upperBound(CLOSED, LocalDate.of(2026, 10, 13)), true));

        // then
        assertThat(all).hasSize(150).startsWith("c0", "c1").endsWith("c149");
        assertThat(window).containsExactly("c0", "c1", "c2");
    }

    @Test
    void oldestFirstReadsBackwards() {
        // given
        buildIndex();

        // when
        List<String> oldest = ids(rmas.listRange("store-1", CLOSED, upperBound(CLOSED, null), false));

        // then
        assertThat(oldest).startsWith("c149", "c148");
    }

    @Test
    void anInvertedRangeIsEmptyNotAnError() {
        // given
        buildIndex();

        // when
        List<String> none = ids(rmas.listRange("store-1", CLOSED + "2026-10-13", CLOSED + "2026-10-01", true));

        // then
        assertThat(none).isEmpty();
    }

    @Test
    void aStatusChangeMovesTheRmaBetweenRanges() {
        // given
        buildIndex();
        RMA a1 = rmas.findById("store-1", "a1");
        a1.setStatus(RMAStatus.Rejected);
        rmas.save(a1);

        // when
        List<String> active = ids(rmas.listRange("store-1", ACTIVE, upperBound(ACTIVE, null), true));
        List<String> closed = ids(rmas.listRange("store-1", lowerBound(CLOSED, LocalDate.of(2026, 10, 12)), upperBound(CLOSED, null), true));

        // then
        assertThat(active).containsExactly("a2", "a3");
        assertThat(closed).contains("a1");
    }

    @Test
    void anEntryWhoseStoredKeyIsStaleIsDropped() {
        // given: an older app version (after a rollback) completed a2 without updating the key
        buildIndex();
        client.updateItem(new UpdateItemRequest().withTableName("RMA")
                .withKey(Map.of("storeId", new AttributeValue("store-1"), "rmaId", new AttributeValue("a2")))
                .withUpdateExpression("SET #s = :s")
                .withExpressionAttributeNames(Map.of("#s", "status"))
                .withExpressionAttributeValues(Map.of(":s", new AttributeValue("Completed"))));

        // when
        List<String> active = ids(rmas.listRange("store-1", ACTIVE, upperBound(ACTIVE, null), true));

        // then
        assertThat(active).containsExactly("a1", "a3");
    }

    @Test
    void withoutTheIndexTheSameRangeComesFromThePartition() {
        // when: V021 has not built the index yet
        List<String> active = ids(rmas.listRange("store-1", ACTIVE, upperBound(ACTIVE, null), true));
        List<String> window = ids(rmas.listRange("store-1", lowerBound(CLOSED, LocalDate.of(2026, 10, 11)),
                upperBound(CLOSED, LocalDate.of(2026, 10, 13)), true));

        // then
        assertThat(active).containsExactly("a2", "a1", "a3");
        assertThat(window).containsExactly("c0", "c1", "c2");
    }
}
