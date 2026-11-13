package pl.commercelink.orders;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.QueryRequest;
import com.amazonaws.services.dynamodbv2.model.QueryResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** The read of cancelled orders behind the Payments page: filtered on the index, loaded in full only when matching. */
@ExtendWith(MockitoExtension.class)
class OrdersRepositoryFindByStatusTest {

    private static final LocalDate FROM = LocalDate.of(2026, 8, 1);
    private static final LocalDate TO = LocalDate.of(2026, 10, 30);

    @Mock
    private AmazonDynamoDB amazonDynamoDB;
    @Mock
    private DynamoDBMapper dynamoDBMapper;

    private OrdersRepository repository;

    @BeforeEach
    void setUp() {
        repository = new OrdersRepository(amazonDynamoDB);
        ReflectionTestUtils.setField(repository, "dynamoDBMapper", dynamoDBMapper);
    }

    private static Order order(String orderId, OrderStatus status) {
        Order order = new Order("store-1");
        order.setOrderId(orderId);
        order.setStatus(status);
        return order;
    }

    private static Map<String, AttributeValue> row(String orderId) {
        return Map.of("orderId", new AttributeValue().withS(orderId));
    }

    @Test
    void readsEveryPageOfTheIndexAndLoadsOnlyTheMatchingOrders() {
        // given: the index answers in two pages
        when(amazonDynamoDB.query(any(QueryRequest.class)))
                .thenReturn(new QueryResult().withItems(List.of(row("o-1")))
                        .withLastEvaluatedKey(Map.of("orderId", new AttributeValue().withS("o-1"))))
                .thenReturn(new QueryResult().withItems(List.of(row("o-2"))));
        when(dynamoDBMapper.batchLoad(anyIterable())).thenReturn(Map.of("Orders",
                List.<Object>of(order("o-1", OrderStatus.Cancelled), order("o-2", OrderStatus.Cancelled))));

        // when
        List<Order> orders = repository.findByStatusOrderedBetween("store-1", OrderStatus.Cancelled, FROM, TO);

        // then
        assertThat(orders).extracting(Order::getOrderId).containsExactlyInAnyOrder("o-1", "o-2");
        ArgumentCaptor<QueryRequest> requests = ArgumentCaptor.forClass(QueryRequest.class);
        verify(amazonDynamoDB, times(2)).query(requests.capture());
        QueryRequest first = requests.getAllValues().get(0);
        assertThat(first.getIndexName()).isEqualTo("StoreIdOrderedAtIndex");
        assertThat(first.getFilterExpression()).isEqualTo("#status = :status");
        assertThat(first.getExpressionAttributeValues().get(":status").getS()).isEqualTo("Cancelled");
        assertThat(first.getExpressionAttributeValues().get(":dateFrom").getS()).startsWith("2026-08-01");
        assertThat(requests.getAllValues().get(1).getExclusiveStartKey()).containsKey("orderId");
    }

    @Test
    void anOrderThatLeftTheStatusSinceTheIndexWasWrittenIsDropped() {
        // given: the index still lists it as cancelled, the record no longer is
        when(amazonDynamoDB.query(any(QueryRequest.class))).thenReturn(new QueryResult().withItems(List.of(row("o-1"))));
        when(dynamoDBMapper.batchLoad(anyIterable())).thenReturn(Map.of("Orders",
                List.<Object>of(order("o-1", OrderStatus.New))));

        // when
        List<Order> orders = repository.findByStatusOrderedBetween("store-1", OrderStatus.Cancelled, FROM, TO);

        // then
        assertThat(orders).isEmpty();
    }

    @Test
    void noMatchingOrderMeansNoFullRead() {
        // given
        when(amazonDynamoDB.query(any(QueryRequest.class))).thenReturn(new QueryResult().withItems(List.of()));

        // when
        List<Order> orders = repository.findByStatusOrderedBetween("store-1", OrderStatus.Cancelled, FROM, TO);

        // then
        assertThat(orders).isEmpty();
        verifyNoInteractions(dynamoDBMapper);
    }
}
