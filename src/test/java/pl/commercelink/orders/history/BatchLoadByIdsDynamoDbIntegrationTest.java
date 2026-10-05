package pl.commercelink.orders.history;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.model.BillingMode;
import com.amazonaws.services.dynamodbv2.model.CreateTableRequest;
import com.amazonaws.services.dynamodbv2.model.KeySchemaElement;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import pl.commercelink.inventory.deliveries.DeliveriesRepository;
import pl.commercelink.inventory.deliveries.Delivery;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.orders.rma.RMA;
import pl.commercelink.orders.rma.RMARepository;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class BatchLoadByIdsDynamoDbIntegrationTest {

    @Container
    static final GenericContainer<?> DYNAMODB =
            new GenericContainer<>(DockerImageName.parse("amazon/dynamodb-local:latest")).withExposedPorts(8000);

    static AmazonDynamoDB client;
    static DynamoDBMapper mapper;

    @BeforeAll
    static void createTables() {
        client = AmazonDynamoDBClientBuilder.standard()
                .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(
                        "http://" + DYNAMODB.getHost() + ":" + DYNAMODB.getMappedPort(8000), "eu-central-1"))
                .withCredentials(new AWSStaticCredentialsProvider(new BasicAWSCredentials("local", "local")))
                .build();
        mapper = new DynamoDBMapper(client);
        createTable(Order.class);
        createTable(Delivery.class);
        createTable(RMA.class);
    }

    // The tables only need their primary key here; index attributes would be rejected as unused definitions.
    static void createTable(Class<?> type) {
        CreateTableRequest request = mapper.generateCreateTableRequest(type).withBillingMode(BillingMode.PAY_PER_REQUEST);
        Set<String> keys = request.getKeySchema().stream().map(KeySchemaElement::getAttributeName).collect(Collectors.toSet());
        request.setGlobalSecondaryIndexes(null);
        request.setLocalSecondaryIndexes(null);
        request.setAttributeDefinitions(request.getAttributeDefinitions().stream()
                .filter(a -> keys.contains(a.getAttributeName())).toList());
        client.createTable(request);
    }

    @Test
    void loadsOrdersOfTheStoreByIdsAcrossSeveralBatches() {
        // given: 130 orders (two batches of at most 100 keys) and one order of another store
        List<String> ids = IntStream.range(0, 130).mapToObj(i -> "order-" + i).toList();
        ids.forEach(id -> mapper.save(order("store-1", id)));
        mapper.save(order("store-2", "foreign"));
        OrdersRepository repository = new OrdersRepository(client);

        // when
        List<Order> orders = repository.findAllByIds("store-1",
                java.util.stream.Stream.concat(ids.stream(), java.util.stream.Stream.of("foreign", "missing", "order-0")).toList());

        // then
        assertThat(orders).extracting(Order::getOrderId).containsExactlyInAnyOrderElementsOf(ids);
    }

    @Test
    void loadsDeliveriesAndRmasByIds() {
        // given
        Delivery delivery = new Delivery();
        delivery.setStoreId("store-1");
        delivery.setDeliveryId("delivery-1");
        mapper.save(delivery);
        RMA rma = new RMA();
        rma.setStoreId("store-1");
        rma.setRmaId("rma-1");
        mapper.save(rma);

        // when
        List<Delivery> deliveries = new DeliveriesRepository(client).findAllByIds("store-1", List.of("delivery-1", "nope"));
        List<RMA> rmas = new RMARepository(client).findAllByIds("store-1", List.of("rma-1", "nope"));

        // then
        assertThat(deliveries).extracting(Delivery::getDeliveryId).containsExactly("delivery-1");
        assertThat(rmas).extracting(RMA::getRmaId).containsExactly("rma-1");
    }

    @Test
    void readsNothingForNoIds() {
        // then
        assertThat(new OrdersRepository(client).findAllByIds("store-1", List.of())).isEmpty();
    }

    static Order order(String storeId, String orderId) {
        Order order = new Order();
        order.setStoreId(storeId);
        order.setOrderId(orderId);
        return order;
    }
}
