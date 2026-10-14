package pl.commercelink.orders;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.model.BillingMode;
import com.amazonaws.services.dynamodbv2.model.Projection;
import com.amazonaws.services.dynamodbv2.model.ProjectionType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import pl.commercelink.testsupport.DynamoDbLocalImage;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The e-mail search of the new RMA page reads one store's StoreIdOrderedAtIndex, newest first, without case. */
@Testcontainers(disabledWithoutDocker = true)
class OrdersByEmailDynamoDbIntegrationTest {

    @Container
    static final GenericContainer<?> DYNAMODB =
            new GenericContainer<>(DynamoDbLocalImage.IMAGE).withExposedPorts(8000);

    static OrdersRepository orders;
    static String older;
    static String newer;

    @BeforeAll
    static void createTheTableAndTheOrders() {
        AmazonDynamoDB client = AmazonDynamoDBClientBuilder.standard()
                .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(
                        "http://" + DYNAMODB.getHost() + ":" + DYNAMODB.getMappedPort(8000), "eu-central-1"))
                .withCredentials(new AWSStaticCredentialsProvider(new BasicAWSCredentials("local", "local")))
                .build();
        DynamoDBMapper mapper = new DynamoDBMapper(client);
        var request = mapper.generateCreateTableRequest(Order.class).withBillingMode(BillingMode.PAY_PER_REQUEST);
        // production projects keys + id, status, e-mail and fulfilment type; ALL here only keeps the test short
        request.getGlobalSecondaryIndexes().forEach(i -> i.withProjection(new Projection().withProjectionType(ProjectionType.ALL)));
        client.createTable(request);
        orders = new OrdersRepository(client);

        older = saved("store-a", "Anna.Nowak@gmail.com", LocalDateTime.of(2026, 3, 1, 10, 0));
        newer = saved("store-a", "anna.nowak@gmail.com", LocalDateTime.of(2026, 10, 2, 10, 0));
        saved("store-a", "jan@example.com", LocalDateTime.of(2026, 10, 5, 10, 0));
        saved("store-b", "anna.nowak@gmail.com", LocalDateTime.of(2026, 10, 6, 10, 0));
    }

    private static String saved(String storeId, String email, LocalDateTime orderedAt) {
        Order order = new Order(storeId);
        // Order.email is a projection of the billing e-mail
        BillingDetails billing = new BillingDetails();
        billing.setEmail(email);
        order.setBillingDetails(billing);
        order.setOrderedAt(orderedAt);
        order.setStatus(OrderStatus.Delivered);
        orders.save(order);
        return order.getOrderId();
    }

    @Test
    void theEmailIsMatchedWithoutCaseAndSpaces() {
        // when
        List<String> found = orders.findOrderIdsByEmail("store-a", "  ANNA.nowak@Gmail.com ", 50);

        // then
        assertThat(found).containsExactly(newer, older);
    }

    @Test
    void theLimitKeepsTheNewest() {
        // when
        List<String> found = orders.findOrderIdsByEmail("store-a", "anna.nowak@gmail.com", 1);

        // then
        assertThat(found).containsExactly(newer);
    }
}
