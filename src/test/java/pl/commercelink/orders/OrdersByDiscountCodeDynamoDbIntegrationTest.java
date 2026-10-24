package pl.commercelink.orders;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.BillingMode;
import com.amazonaws.services.dynamodbv2.model.Projection;
import com.amazonaws.services.dynamodbv2.model.ProjectionType;
import com.amazonaws.services.dynamodbv2.model.ScanRequest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import pl.commercelink.testsupport.DynamoDbLocalImage;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** The details page of a discount code reads its orders from DiscountCodeIndex, newest first, all pages. */
@Testcontainers(disabledWithoutDocker = true)
class OrdersByDiscountCodeDynamoDbIntegrationTest {

    @Container
    static final GenericContainer<?> DYNAMODB =
            new GenericContainer<>(DynamoDbLocalImage.IMAGE).withExposedPorts(8000);

    static AmazonDynamoDB client;
    static OrdersRepository orders;
    static String withoutCode;
    static String first;
    static String lastSecondOfTheDay;
    static String newest;

    @BeforeAll
    static void createTheTableAndTheOrders() {
        client = AmazonDynamoDBClientBuilder.standard()
                .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(
                        "http://" + DYNAMODB.getHost() + ":" + DYNAMODB.getMappedPort(8000), "eu-central-1"))
                .withCredentials(new AWSStaticCredentialsProvider(new BasicAWSCredentials("local", "local")))
                .build();
        var request = new DynamoDBMapper(client).generateCreateTableRequest(Order.class).withBillingMode(BillingMode.PAY_PER_REQUEST);
        // DiscountCodeIndex as V023 creates it; the other indexes only need to exist
        request.getGlobalSecondaryIndexes().forEach(index -> index.withProjection("DiscountCodeIndex".equals(index.getIndexName())
                ? new Projection().withProjectionType(ProjectionType.INCLUDE).withNonKeyAttributes("email", "totalPrice", "discountAmount", "status")
                : new Projection().withProjectionType(ProjectionType.KEYS_ONLY)));
        client.createTable(request);
        orders = new OrdersRepository(client);

        first = saved("store-a", "LATO-10", LocalDateTime.of(2026, 10, 1, 0, 0), "anna@example.com", 900, 100, OrderStatus.Delivered);
        lastSecondOfTheDay = saved("store-a", "LATO-10", LocalDateTime.of(2026, 10, 15, 23, 59, 59, 500_000_000), "jan@example.com", 450, 50, OrderStatus.Cancelled);
        newest = saved("store-a", "LATO-10", LocalDateTime.of(2026, 10, 20, 9, 30), "ewa@example.com", 1800, 200, OrderStatus.New);
        saved("store-b", "LATO-10", LocalDateTime.of(2026, 10, 10, 12, 0), "inny@example.com", 300, 30, OrderStatus.New);
        saved("store-a", "ZIMA-5", LocalDateTime.of(2026, 10, 11, 12, 0), "anna@example.com", 300, 15, OrderStatus.New);
        withoutCode = saved("store-a", null, LocalDateTime.of(2026, 10, 12, 12, 0), "anna@example.com", 300, 0, OrderStatus.New);
    }

    private static String saved(String storeId, String code, LocalDateTime orderedAt, String email, double totalPrice,
                                double discountAmount, OrderStatus status) {
        Order order = new Order(storeId);
        BillingDetails billing = new BillingDetails();
        billing.setEmail(email);
        order.setBillingDetails(billing);
        order.setOrderedAt(orderedAt);
        order.setStatus(status);
        order.setTotalPrice(totalPrice);
        order.setDiscountCode(code);
        order.setDiscountAmount(discountAmount);
        orders.save(order);
        return order.getOrderId();
    }

    private static List<String> ids(List<DiscountedOrderEntry> entries) {
        return entries.stream().map(DiscountedOrderEntry::orderId).toList();
    }

    @Test
    void returnsOnlyTheOrdersOfTheStoreNewestFirst() {
        // when
        List<DiscountedOrderEntry> found = orders.findAllByDiscountCode("store-a", "LATO-10", null, null).orElseThrow();

        // then
        assertThat(ids(found)).containsExactly(newest, lastSecondOfTheDay, first);
    }

    @Test
    void anEntryCarriesWhatTheDetailsPageShows() {
        // when
        DiscountedOrderEntry entry = orders.findAllByDiscountCode("store-a", "LATO-10", null, null).orElseThrow().get(0);

        // then
        assertThat(entry).isEqualTo(new DiscountedOrderEntry(newest, LocalDateTime.of(2026, 10, 20, 9, 30),
                "ewa@example.com", 1800, 200, OrderStatus.New));
    }

    @Test
    void theDaysNarrowTheOrdersInclusively() {
        // when
        List<DiscountedOrderEntry> found = orders.findAllByDiscountCode("store-a", "LATO-10",
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 15)).orElseThrow();

        // then
        assertThat(ids(found)).containsExactly(lastSecondOfTheDay, first);
    }

    @Test
    void everyPageIsRead() {
        // when
        List<DiscountedOrderEntry> found = orders.findAllByDiscountCode("store-a", "LATO-10", null, null, 1).orElseThrow();

        // then
        assertThat(ids(found)).containsExactly(newest, lastSecondOfTheDay, first);
    }

    @Test
    void onlyTheOrdersWithThatCodeAreListed() {
        // when
        List<DiscountedOrderEntry> found = orders.findAllByDiscountCode("store-a", "ZIMA-5", null, null).orElseThrow();

        // then
        assertThat(found).hasSize(1).allMatch(entry -> entry.discountAmount() == 15);
    }

    @Test
    void ordersWithoutCodeAreNotIndexed() {
        // when
        List<Map<String, AttributeValue>> indexed = client.scan(new ScanRequest("Orders").withIndexName("DiscountCodeIndex")).getItems();

        // then
        assertThat(indexed).hasSize(5).noneMatch(item -> withoutCode.equals(item.get("orderId").getS()));
    }
}
