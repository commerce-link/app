package pl.commercelink.orders;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.model.AmazonDynamoDBException;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** The cancelled orders awaiting a refund behind the orders list and the Payments page: read from their own sparse index. */
@ExtendWith(MockitoExtension.class)
class OrdersRepositoryRefundDueIndexTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 30);

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

    private static Order cancelledHolding(String orderId, double paid) {
        Order order = new Order("store-1");
        order.setOrderId(orderId);
        order.setStatus(OrderStatus.Cancelled);
        order.addPayment(Payment.bankTransfer("REF-" + orderId, "Jan", paid));
        return order;
    }

    private static Order cancelledAndRefunded(String orderId) {
        Order order = cancelledHolding(orderId, 250);
        order.addPayment(new Payment("REF-OUT", "Jan", PaymentSource.BankTransfer, PaymentDirection.Outgoing, -250, 0, null, null));
        return order;
    }

    private static Map<String, AttributeValue> row(String orderId) {
        return Map.of("orderId", new AttributeValue().withS(orderId));
    }

    @Test
    void readsEveryPageOfTheStoresRefundsDueAndLoadsOnlyThoseOrders() {
        // given: the index answers in two pages
        when(amazonDynamoDB.query(any(QueryRequest.class)))
                .thenReturn(new QueryResult().withItems(List.of(row("o-1")))
                        .withLastEvaluatedKey(Map.of("orderId", new AttributeValue().withS("o-1"))))
                .thenReturn(new QueryResult().withItems(List.of(row("o-2"))));
        when(dynamoDBMapper.batchLoad(anyIterable())).thenReturn(Map.of("Orders",
                List.<Object>of(cancelledHolding("o-1", 250), cancelledHolding("o-2", 99))));

        // when
        List<Order> orders = repository.findCancelledAwaitingRefund("store-1", TODAY);

        // then
        assertThat(orders).extracting(Order::getOrderId).containsExactlyInAnyOrder("o-1", "o-2");
        ArgumentCaptor<QueryRequest> requests = ArgumentCaptor.forClass(QueryRequest.class);
        verify(amazonDynamoDB, times(2)).query(requests.capture());
        QueryRequest first = requests.getAllValues().get(0);
        assertThat(first.getIndexName()).isEqualTo("StoreIdRefundDueIndex");
        assertThat(first.getKeyConditionExpression()).isEqualTo("storeId = :storeId");
        assertThat(first.getFilterExpression()).isNull();
        assertThat(first.getExpressionAttributeValues().get(":storeId").getS()).isEqualTo("store-1");
        assertThat(requests.getAllValues().get(1).getExclusiveStartKey()).containsKey("orderId");
    }

    @Test
    void anOrderWhoseRefundWasRecordedSinceTheIndexWasWrittenIsDropped() {
        // given: the index still lists both, one record already holds its refund
        when(amazonDynamoDB.query(any(QueryRequest.class)))
                .thenReturn(new QueryResult().withItems(List.of(row("o-1"), row("o-2"))));
        when(dynamoDBMapper.batchLoad(anyIterable())).thenReturn(Map.of("Orders",
                List.<Object>of(cancelledHolding("o-1", 250), cancelledAndRefunded("o-2"))));

        // when
        List<Order> orders = repository.findCancelledAwaitingRefund("store-1", TODAY);

        // then
        assertThat(orders).extracting(Order::getOrderId).containsExactly("o-1");
    }

    @Test
    void noRefundDueMeansNoFullRead() {
        // given
        when(amazonDynamoDB.query(any(QueryRequest.class))).thenReturn(new QueryResult().withItems(List.of()));

        // when
        List<Order> orders = repository.findCancelledAwaitingRefund("store-1", TODAY);

        // then
        assertThat(orders).isEmpty();
        verifyNoInteractions(dynamoDBMapper);
    }

    @Test
    void whileTheIndexIsBeingBuiltTheCancelledOrdersOfTheLastNinetyDaysAreReadInstead() {
        // given: right after V029 DynamoDB still backfills the index
        when(amazonDynamoDB.query(any(QueryRequest.class)))
                .thenThrow(new AmazonDynamoDBException("Cannot read from backfilling global secondary index: StoreIdRefundDueIndex"))
                .thenReturn(new QueryResult().withItems(List.of(row("o-1"), row("o-2"))));
        when(dynamoDBMapper.batchLoad(anyIterable())).thenReturn(Map.of("Orders",
                List.<Object>of(cancelledHolding("o-1", 250), cancelledAndRefunded("o-2"))));

        // when
        List<Order> orders = repository.findCancelledAwaitingRefund("store-1", TODAY);

        // then
        assertThat(orders).extracting(Order::getOrderId).containsExactly("o-1");
        ArgumentCaptor<QueryRequest> requests = ArgumentCaptor.forClass(QueryRequest.class);
        verify(amazonDynamoDB, times(2)).query(requests.capture());
        QueryRequest fallback = requests.getAllValues().get(1);
        assertThat(fallback.getIndexName()).isEqualTo("StoreIdOrderedAtIndex");
        assertThat(fallback.getExpressionAttributeValues().get(":dateFrom").getS())
                .startsWith(TODAY.minusDays(OrdersRepository.CANCELLED_AWAITING_REFUND_WINDOW_DAYS).toString());
        assertThat(fallback.getExpressionAttributeValues().get(":status").getS()).isEqualTo("Cancelled");
    }

    @Test
    void anotherDatabaseErrorIsNotHiddenBehindTheOldRead() {
        // given
        when(amazonDynamoDB.query(any(QueryRequest.class)))
                .thenThrow(new AmazonDynamoDBException("Throughput exceeds the current capacity of your table"));

        // when / then
        assertThatThrownBy(() -> repository.findCancelledAwaitingRefund("store-1", TODAY))
                .isInstanceOf(AmazonDynamoDBException.class);
        verify(amazonDynamoDB).query(any(QueryRequest.class));
    }
}
