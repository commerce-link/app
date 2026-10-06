package pl.commercelink.orders;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.model.BillingMode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The reads behind a batch of printed order cards, against DynamoDB Local: one batch of a store's orders in the asked
 * order (another store's order with a known id does not come back) and each order's items by a query on its own
 * partition, in position order.
 */
@Testcontainers(disabledWithoutDocker = true)
class OrderCardsReadDynamoDbIntegrationTest {

    @Container
    static final GenericContainer<?> DYNAMODB =
            new GenericContainer<>(DockerImageName.parse("amazon/dynamodb-local:latest")).withExposedPorts(8000);

    static OrdersRepository orders;
    static OrderItemsRepository items;
    static String first;
    static String second;
    static String foreign;

    @BeforeAll
    static void createTheTablesAndTheOrders() {
        AmazonDynamoDB client = AmazonDynamoDBClientBuilder.standard()
                .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(
                        "http://" + DYNAMODB.getHost() + ":" + DYNAMODB.getMappedPort(8000), "eu-central-1"))
                .withCredentials(new AWSStaticCredentialsProvider(new BasicAWSCredentials("local", "local")))
                .build();
        DynamoDBMapper mapper = new DynamoDBMapper(client);
        client.createTable(mapper.generateCreateTableRequest(Order.class).withBillingMode(BillingMode.PAY_PER_REQUEST));
        client.createTable(mapper.generateCreateTableRequest(OrderItem.class).withBillingMode(BillingMode.PAY_PER_REQUEST));
        orders = new OrdersRepository(client);
        items = new OrderItemsRepository(client);

        first = savedOrder("store-a");
        second = savedOrder("store-a");
        foreign = savedOrder("store-b");
        items.save(new OrderItem(first, "CPU", "Late", 1, 10, "SKU", false, 2));
        items.save(new OrderItem(first, "CPU", "Early", 1, 10, "SKU", false, 0));
        items.save(new OrderItem(foreign, "CPU", "Foreign", 1, 10, "SKU", false, 0));
    }

    private static String savedOrder(String storeId) {
        Order order = new Order(storeId);
        order.setStatus(OrderStatus.New);
        orders.save(order);
        return order.getOrderId();
    }

    @Test
    void theOrdersOfOneStoreComeBackInTheAskedOrderWithoutAnotherStoresOrder() {
        // when
        List<Order> found = orders.findByIds("store-a", List.of(second, foreign, first, "missing"));

        // then
        assertThat(found).extracting(Order::getOrderId).containsExactly(second, first);
    }

    @Test
    void eachOrdersItemsAreReadFromItsOwnPartitionInPositionOrder() {
        // when
        Map<String, List<OrderItem>> found = items.findByOrderIds(List.of(first, second));

        // then
        assertThat(found.get(first)).extracting(OrderItem::getName).containsExactly("Early", "Late");
        assertThat(found.get(second)).isEmpty();
    }
}
