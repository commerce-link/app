package pl.commercelink.orders;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperTableModel;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.QueryRequest;
import com.amazonaws.services.dynamodbv2.model.QueryResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrdersRepositoryActiveOrdersTest {

    private static final Map<String, AttributeValue> NEXT_PAGE =
            Map.of("storeId", new AttributeValue().withS("store-1"));

    @Mock
    private AmazonDynamoDB amazonDynamoDB;

    @Captor
    private ArgumentCaptor<QueryRequest> queryRequestCaptor;

    private OrdersRepository ordersRepository;
    private DynamoDBMapperTableModel<Order> tableModel;

    @BeforeEach
    void setUp() {
        ordersRepository = new OrdersRepository(amazonDynamoDB);
        tableModel = new DynamoDBMapper(amazonDynamoDB).getTableModel(Order.class);
    }

    @Test
    @DisplayName("queries the table by its partition key instead of scanning every tenant")
    void queriesByPartitionKey() {
        // given
        when(amazonDynamoDB.query(any(QueryRequest.class))).thenReturn(page(List.of(order()), null));

        // when
        ordersRepository.findAllActiveOrders("store-1");

        // then
        verify(amazonDynamoDB).query(queryRequestCaptor.capture());
        QueryRequest request = queryRequestCaptor.getValue();
        assertThat(request.getIndexName()).isNull();
        assertThat(request.getKeyConditionExpression()).isEqualTo("storeId = :storeId");
        assertThat(request.getExpressionAttributeValues().get(":storeId").getS()).isEqualTo("store-1");
    }

    @Test
    @DisplayName("excludes completed and cancelled orders through a filter expression")
    void excludesClosedOrders() {
        // given
        when(amazonDynamoDB.query(any(QueryRequest.class))).thenReturn(page(List.of(order()), null));

        // when
        ordersRepository.findAllActiveOrders("store-1");

        // then
        verify(amazonDynamoDB).query(queryRequestCaptor.capture());
        QueryRequest request = queryRequestCaptor.getValue();
        assertThat(request.getFilterExpression())
                .isEqualTo("#status <> :statusCompleted AND #status <> :statusCancelled");
        assertThat(request.getExpressionAttributeNames()).containsEntry("#status", "status");
        assertThat(request.getExpressionAttributeValues().get(":statusCompleted").getS())
                .isEqualTo(OrderStatus.Completed.name());
        assertThat(request.getExpressionAttributeValues().get(":statusCancelled").getS())
                .isEqualTo(OrderStatus.Cancelled.name());
    }

    @Test
    @DisplayName("follows the query across every page instead of stopping at the first one")
    void drainsEveryPage() {
        // given
        when(amazonDynamoDB.query(any(QueryRequest.class)))
                .thenReturn(page(List.of(order()), NEXT_PAGE))
                .thenReturn(page(List.of(order(), order()), null));

        // when
        List<Order> activeOrders = ordersRepository.findAllActiveOrders("store-1");

        // then
        assertThat(activeOrders).hasSize(3);
        verify(amazonDynamoDB, times(2)).query(queryRequestCaptor.capture());
        assertThat(queryRequestCaptor.getAllValues().get(0).getExclusiveStartKey()).isNull();
        assertThat(queryRequestCaptor.getAllValues().get(1).getExclusiveStartKey()).isEqualTo(NEXT_PAGE);
    }

    private Order order() {
        Order order = new Order("store-1");
        order.setEmail("buyer@example.com");
        return order;
    }

    private QueryResult page(List<Order> orders, Map<String, AttributeValue> lastEvaluatedKey) {
        return new QueryResult()
                .withItems(orders.stream().map(tableModel::convert).toList())
                .withLastEvaluatedKey(lastEvaluatedKey);
    }
}
