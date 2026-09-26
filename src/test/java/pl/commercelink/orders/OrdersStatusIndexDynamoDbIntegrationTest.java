package pl.commercelink.orders;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.model.CreateTableRequest;
import com.amazonaws.services.dynamodbv2.model.BillingMode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import pl.commercelink.migration.V016_AddStoreIdStatusIndex;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The orders list reads only open orders through StoreIdStatusIndex, against DynamoDB Local: before V016 has added
 * the index the store's partition is read instead (the list keeps working while the index is built after a deploy);
 * after it, only the asked-for statuses of the one store come back, and running V016 again changes nothing.
 */
@Testcontainers(disabledWithoutDocker = true)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OrdersStatusIndexDynamoDbIntegrationTest {

    @Container
    static final GenericContainer<?> DYNAMODB =
            new GenericContainer<>(DockerImageName.parse("amazon/dynamodb-local:latest")).withExposedPorts(8000);

    static AmazonDynamoDB client;
    static OrdersRepository orders;

    private static final List<OrderStatus> OPEN = List.of(OrderStatus.New, OrderStatus.Blocked, OrderStatus.Assembly,
            OrderStatus.Assembled, OrderStatus.Realization, OrderStatus.Shipping, OrderStatus.Delivered);

    @BeforeAll
    static void createTheOrdersTableWithoutTheNewIndex() {
        client = AmazonDynamoDBClientBuilder.standard()
                .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(
                        "http://" + DYNAMODB.getHost() + ":" + DYNAMODB.getMappedPort(8000), "eu-central-1"))
                .withCredentials(new AWSStaticCredentialsProvider(new BasicAWSCredentials("local", "local")))
                .build();
        // on demand, like the real table (V001): an index added to it needs no throughput of its own
        CreateTableRequest request = new DynamoDBMapper(client).generateCreateTableRequest(pl.commercelink.orders.Order.class)
                .withBillingMode(BillingMode.PAY_PER_REQUEST);
        client.createTable(request);
        orders = new OrdersRepository(client);

        save("store-a", OrderStatus.New, OrderStatus.New, OrderStatus.Assembled,
                OrderStatus.Completed, OrderStatus.Completed, OrderStatus.Completed, OrderStatus.Cancelled);
        save("store-b", OrderStatus.New);
    }

    private static void save(String storeId, OrderStatus... statuses) {
        for (OrderStatus status : statuses) {
            pl.commercelink.orders.Order order = new pl.commercelink.orders.Order(storeId);
            order.setStatus(status);
            orders.save(order);
        }
    }

    @Test
    @Order(1)
    void beforeTheIndexExistsTheStorePartitionIsReadAndFiltered() {
        List<pl.commercelink.orders.Order> open = orders.findByStoreAndStatuses("store-a", OPEN);

        assertThat(open).extracting(pl.commercelink.orders.Order::getStatus)
                .containsExactlyInAnyOrder(OrderStatus.New, OrderStatus.New, OrderStatus.Assembled);
    }

    @Test
    @Order(2)
    void afterV016OnlyTheAskedForStatusesOfTheStoreAreRead() {
        new V016_AddStoreIdStatusIndex(client).execute();
        assertThat(client.describeTable("Orders").getTable().getGlobalSecondaryIndexes())
                .anyMatch(index -> V016_AddStoreIdStatusIndex.INDEX.equals(index.getIndexName()));

        assertThat(orders.findByStoreAndStatuses("store-a", OPEN)).extracting(pl.commercelink.orders.Order::getStatus)
                .containsExactlyInAnyOrder(OrderStatus.New, OrderStatus.New, OrderStatus.Assembled);
        assertThat(orders.findByStoreAndStatuses("store-a", List.of(OrderStatus.Completed))).hasSize(3);
        assertThat(orders.findByStoreAndStatuses("store-b", OPEN)).hasSize(1);
        assertThat(orders.findByStoreAndStatuses("store-c", OPEN)).isEmpty();
        assertThat(orders.findByStoreAndStatuses("store-a", List.of())).isEmpty();
    }

    @Test
    @Order(3)
    void runningV016AgainChangesNothingAndAStatusChangeMovesTheOrderInTheIndex() {
        new V016_AddStoreIdStatusIndex(client).execute();

        pl.commercelink.orders.Order assembled = orders.findByStoreAndStatuses("store-a", List.of(OrderStatus.Assembled)).get(0);
        assembled.setStatus(OrderStatus.Completed);
        orders.save(assembled);

        assertThat(orders.findByStoreAndStatuses("store-a", OPEN)).extracting(pl.commercelink.orders.Order::getStatus)
                .containsExactlyInAnyOrder(OrderStatus.New, OrderStatus.New);
    }
}
