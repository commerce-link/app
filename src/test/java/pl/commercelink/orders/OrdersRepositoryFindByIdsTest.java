package pl.commercelink.orders;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** The batch read behind the order cards printed from the orders list. */
@ExtendWith(MockitoExtension.class)
class OrdersRepositoryFindByIdsTest {

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

    private static Order order(String storeId, String orderId) {
        Order order = new Order(storeId);
        order.setOrderId(orderId);
        return order;
    }

    @Test
    void returnsTheFoundOrdersInTheGivenOrderSkippingMissingOnes() {
        // given: a batch answers in no particular order and leaves out keys it has no item for
        when(dynamoDBMapper.batchLoad(anyIterable())).thenReturn(Map.of("Orders",
                List.<Object>of(order("store-1", "o-3"), order("store-1", "o-1"))));

        // when
        List<Order> orders = repository.findByIds("store-1", List.of("o-1", "o-2", "o-3"));

        // then
        assertThat(orders).extracting(Order::getOrderId).containsExactly("o-1", "o-3");
    }

    @Test
    @SuppressWarnings("unchecked")
    void asksForEachIdOnceAndOnlyWithinTheGivenStore() {
        // given
        when(dynamoDBMapper.batchLoad(anyIterable())).thenReturn(Map.of());

        // when
        repository.findByIds("store-1", List.of("o-2", "o-1", "o-2"));

        // then
        ArgumentCaptor<Iterable<Object>> keys = ArgumentCaptor.forClass(Iterable.class);
        verify(dynamoDBMapper).batchLoad(keys.capture());
        assertThat(keys.getValue()).extracting(key -> ((Order) key).getStoreId() + "/" + ((Order) key).getOrderId())
                .containsExactly("store-1/o-2", "store-1/o-1");
    }

    @Test
    void anOrderOfAnotherStoreIsNeverReturned() {
        // given: whatever the batch hands back, only the asked store's orders count
        when(dynamoDBMapper.batchLoad(anyIterable())).thenReturn(Map.of("Orders",
                List.<Object>of(order("store-2", "o-1"))));

        // when
        List<Order> orders = repository.findByIds("store-1", List.of("o-1"));

        // then
        assertThat(orders).isEmpty();
    }

    @Test
    void readsNothingForNoIds() {
        // when
        List<Order> orders = repository.findByIds("store-1", List.of());

        // then
        assertThat(orders).isEmpty();
        verifyNoInteractions(dynamoDBMapper);
    }
}
