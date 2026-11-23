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
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderStatus;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.orders.Payment;
import pl.commercelink.orders.PaymentDirection;
import pl.commercelink.orders.PaymentSource;
import pl.commercelink.testsupport.DynamoDbLocalImage;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;

/** V029 on an Orders table shaped like production before it: the order date index with the status, no StoreIdRefundDueIndex. */
@Testcontainers(disabledWithoutDocker = true)
class V029AddRefundDueIndexDynamoDbIntegrationTest {

    private static final LocalDate TODAY = LocalDate.now();

    @Container
    static final GenericContainer<?> DYNAMODB =
            new GenericContainer<>(DynamoDbLocalImage.IMAGE).withExposedPorts(8000);

    AmazonDynamoDB client;
    DynamoDBMapper mapper;

    @BeforeEach
    void connect() {
        client = AmazonDynamoDBClientBuilder.standard()
                .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(
                        "http://" + DYNAMODB.getHost() + ":" + DYNAMODB.getMappedPort(8000), "eu-central-1"))
                .withCredentials(new AWSStaticCredentialsProvider(new BasicAWSCredentials("local", "local")))
                .build();
        mapper = new DynamoDBMapper(client);
        if (client.listTables().getTableNames().contains("Orders")) {
            client.deleteTable("Orders");
        }
        client.createTable(ordersTableBefore().withBillingMode(BillingMode.PAY_PER_REQUEST));
    }

    @AfterEach
    void letTheIndexBuildBeforeTheNextTestDropsTheTable() {
        await(this::indexesActive, "the Orders indexes to be built");
    }

    @Test
    void addsTheIndexByStoreAndRefundKeyWithKeysOnly() {
        // when
        migration().execute();

        // then
        GlobalSecondaryIndexDescription index = refundDueIndex();
        assertThat(index.getKeySchema()).containsExactly(
                new KeySchemaElement("storeId", KeyType.HASH), new KeySchemaElement("refundDueSortKey", KeyType.RANGE));
        assertThat(index.getProjection().getProjectionType()).isEqualTo("KEYS_ONLY");
    }

    @Test
    void anExistingIndexIsLeftAlone() {
        // given
        migration().execute();
        await(this::indexesActive, "the first run's index");
        int indexes = client.describeTable("Orders").getTable().getGlobalSecondaryIndexes().size();

        // when
        migration().execute();

        // then
        assertThat(client.describeTable("Orders").getTable().getGlobalSecondaryIndexes()).hasSize(indexes);
    }

    @Test
    void anOrderStoredWithoutTheKeyIsListedOnlyAfterItsNextSave() {
        // given: cancelled with the customer's payment on it by a version that does not write the key
        Order owedBack = storedByAnEarlierVersion(cancelled(10, 500));
        OrdersRepository repository = new OrdersRepository(client);

        // when: the unit creates the index and gives no order its key
        migration().execute();
        await(this::indexesActive, "the new index");

        // then
        assertThat(storedKey(owedBack)).isNull();
        assertThat(repository.findCancelledAwaitingRefund("store-1", TODAY)).isEmpty();

        // when: the order is saved again, whatever the reason
        repository.save(repository.findById("store-1", owedBack.getOrderId()));

        // then
        await(() -> indexedOrderIds().contains(owedBack.getOrderId()), "the order to enter the index");
        assertThat(repository.findCancelledAwaitingRefund("store-1", TODAY))
                .extracting(Order::getOrderId).containsExactly(owedBack.getOrderId());
    }

    @Test
    void aSavedOrderEntersTheIndexWithItsPaymentAndLeavesItWithItsRefund() {
        // given
        migration().execute();
        await(this::indexesActive, "the new index");
        OrdersRepository repository = new OrdersRepository(client);
        Order order = order(OrderStatus.Assembly, 3, 1249);

        // when: cancelled while holding the customer's payment
        order.markCancelled();
        repository.save(order);

        // then: keyed by when it was cancelled
        assertThat(storedKey(order)).isEqualTo(stored(order).get("cancelledAt").getS()).isNotNull();
        await(() -> indexedOrderIds().contains(order.getOrderId()), "the order to enter the index");
        assertThat(repository.findCancelledAwaitingRefund("store-1", TODAY))
                .extracting(Order::getOrderId).containsExactly(order.getOrderId());

        // when: the refund is recorded
        order.addPayment(refund(1249));
        repository.save(order);

        // then: the key is gone from the record, and with it the order from the index; the cancellation date stays
        assertThat(stored(order)).doesNotContainKey("refundDueSortKey").containsKey("cancelledAt");
        await(() -> indexedOrderIds().isEmpty(), "the order to leave the index");
        assertThat(repository.findCancelledAwaitingRefund("store-1", TODAY)).isEmpty();
    }

    private V029_AddRefundDueIndex migration() {
        return new V029_AddRefundDueIndex(client, millis -> { }, Duration.ofSeconds(5));
    }

    private static Order cancelled(int daysAgo, double paid) {
        return order(OrderStatus.Cancelled, daysAgo, paid);
    }

    private static Order order(OrderStatus status, int daysAgo, double paid) {
        Order order = new Order("store-1");
        order.setStatus(status);
        order.setTotalPrice(1249);
        order.setOrderedAt(LocalDateTime.now().minusDays(daysAgo).withNano(0));
        if (paid > 0) {
            order.addPayment(Payment.bankTransfer("REF-IN", "Jan", paid));
        }
        return order;
    }

    private static Payment refund(double amount) {
        return new Payment("REF-OUT", "Jan", PaymentSource.BankTransfer, PaymentDirection.Outgoing, -amount, 0, null, null);
    }

    private Order storedByAnEarlierVersion(Order order) {
        mapper.save(order);
        client.updateItem(new UpdateItemRequest().withTableName("Orders").withKey(key(order))
                .withUpdateExpression("REMOVE refundDueSortKey"));
        return order;
    }


    private static Map<String, AttributeValue> key(Order order) {
        return Map.of("storeId", new AttributeValue(order.getStoreId()), "orderId", new AttributeValue(order.getOrderId()));
    }

    private Map<String, AttributeValue> stored(Order order) {
        return client.getItem(new GetItemRequest().withTableName("Orders").withKey(key(order)).withConsistentRead(true)).getItem();
    }

    private String storedKey(Order order) {
        AttributeValue value = stored(order).get("refundDueSortKey");
        return value == null ? null : value.getS();
    }

    private List<String> indexedOrderIds() {
        return client.query(new QueryRequest().withTableName("Orders").withIndexName(V029_AddRefundDueIndex.INDEX)
                        .withKeyConditionExpression("storeId = :storeId")
                        .withExpressionAttributeValues(Map.of(":storeId", new AttributeValue("store-1"))))
                .getItems().stream().map(item -> item.get("orderId").getS()).toList();
    }

    // refundDueSortKey has no index annotation, so the table generated from the entity is the one before V029; the order
    // date index carries the status as in production (V001)
    private CreateTableRequest ordersTableBefore() {
        CreateTableRequest request = mapper.generateCreateTableRequest(Order.class);
        request.getGlobalSecondaryIndexes().forEach(index -> index.withProjection(
                "StoreIdOrderedAtIndex".equals(index.getIndexName())
                        ? new Projection().withProjectionType(ProjectionType.INCLUDE).withNonKeyAttributes("status", "email", "fulfilmentType")
                        : new Projection().withProjectionType(ProjectionType.KEYS_ONLY)));
        return request;
    }

    private GlobalSecondaryIndexDescription refundDueIndex() {
        return client.describeTable("Orders").getTable().getGlobalSecondaryIndexes().stream()
                .filter(index -> V029_AddRefundDueIndex.INDEX.equals(index.getIndexName()))
                .findFirst().orElseThrow();
    }

    // dynamodb-local reports no status at all for an index that is ready
    private boolean indexesActive() {
        if (!client.listTables().getTableNames().contains("Orders")) {
            return true;
        }
        List<GlobalSecondaryIndexDescription> indexes = client.describeTable("Orders").getTable().getGlobalSecondaryIndexes();
        return indexes == null || indexes.stream()
                .allMatch(index -> index.getIndexStatus() == null || "ACTIVE".equals(index.getIndexStatus()));
    }

    // V029 creates the index without waiting for it, and an index follows the table with a delay
    private static void await(BooleanSupplier done, String what) {
        long deadline = System.currentTimeMillis() + Duration.ofMinutes(1).toMillis();
        while (!done.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) {
                throw new IllegalStateException("Timed out waiting for " + what);
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
    }
}
