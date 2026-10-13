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
import pl.commercelink.orders.fulfilment.FulfilmentType;
import pl.commercelink.testsupport.DynamoDbLocalImage;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The reports page reads every non-new order of a date range from StoreIdOrderedAtIndex, page after page. */
@Testcontainers(disabledWithoutDocker = true)
class OrdersPastOrdersDynamoDbIntegrationTest {

    @Container
    static final GenericContainer<?> DYNAMODB =
            new GenericContainer<>(DynamoDbLocalImage.IMAGE).withExposedPorts(8000);

    static OrdersRepository orders;
    static final LocalDate FROM = LocalDate.of(2026, 5, 1);
    static final LocalDate TO = LocalDate.of(2026, 5, 31);

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

        // 25 orders on 1-25 May, one at the very end of the range, one just after it, one new, one of another store
        for (int day = 1; day <= 25; day++) {
            save("store-a", OrderStatus.Completed, LocalDateTime.of(2026, 5, day, 12, 0));
        }
        save("store-a", OrderStatus.Completed, LocalDateTime.of(2026, 5, 31, 23, 59, 59, 500_000_000));
        save("store-a", OrderStatus.Completed, LocalDateTime.of(2026, 6, 1, 0, 0, 0));
        save("store-a", OrderStatus.New, LocalDateTime.of(2026, 5, 10, 8, 0));
        save("store-b", OrderStatus.Completed, LocalDateTime.of(2026, 5, 10, 8, 0));
    }

    private static void save(String storeId, OrderStatus status, LocalDateTime orderedAt) {
        Order order = new Order(storeId);
        order.setStatus(status);
        order.setOrderedAt(orderedAt);
        BillingDetails billing = new BillingDetails();
        billing.setEmail("a@b.pl");
        order.setBillingDetails(billing);
        order.setFulfilmentType(FulfilmentType.values()[0]);
        orders.save(order);
    }

    @Test
    void readsTheWholeRangeAcrossDynamoDbPages() {
        // when — 4 index entries per DynamoDB call, so the range takes several calls
        List<OrderIndexEntry> entries = orders.findAllPastOrders(FROM, TO, "store-a", 4);

        // then
        assertThat(entries).hasSize(26);
        assertThat(orders.findAllPastOrders(FROM, TO, "store-a")).hasSize(26);
    }

    @Test
    void includesTheLastFractionOfTheEndDayAndExcludesTheNextDay() {
        // when
        List<OrderIndexEntry> entries = orders.findAllPastOrders(FROM, TO, "store-a");

        // then
        assertThat(entries).extracting(OrderIndexEntry::getOrderedAt)
                .contains(LocalDateTime.of(2026, 5, 31, 23, 59, 59, 500_000_000))
                .doesNotContain(LocalDateTime.of(2026, 6, 1, 0, 0, 0));
    }

    @Test
    void excludesNewOrdersAndOrdersOfOtherStores() {
        // when
        List<OrderIndexEntry> entries = orders.findAllPastOrders(FROM, TO, "store-a");

        // then
        assertThat(entries).extracting(OrderIndexEntry::getStatus).doesNotContain(OrderStatus.New);
        assertThat(entries).extracting(OrderIndexEntry::getStoreId).containsOnly("store-a");
    }
}
