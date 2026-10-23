package pl.commercelink.orders;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBScanExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.PaginatedQueryList;
import com.amazonaws.services.dynamodbv2.datamodeling.PaginatedScanList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderItemsRepositoryTest {

    @Mock
    private AmazonDynamoDB amazonDynamoDB;
    @Mock
    private DynamoDBMapper dynamoDBMapper;

    private OrderItemsRepository repository;

    @BeforeEach
    void setUp() {
        repository = new OrderItemsRepository(amazonDynamoDB);
        ReflectionTestUtils.setField(repository, "dynamoDBMapper", dynamoDBMapper);
    }

    @Test
    @DisplayName("scanAndSort orders items ascending by position")
    void scanAndSortOrdersItemsAscendingByPosition() {
        // given
        OrderItem third = orderItem("id-3", 2);
        OrderItem first = orderItem("id-1", 0);
        OrderItem second = orderItem("id-2", 1);
        stubScan(third, first, second);

        // when
        List<OrderItem> sorted = repository.scanAndSort(new DynamoDBScanExpression());

        // then
        assertThat(sorted).extracting(OrderItem::getPosition).containsExactly(0, 1, 2);
    }

    @Test
    @DisplayName("scanAndSort breaks position ties by unit price descending")
    void scanAndSortBreaksPositionTiesByUnitPriceDescending() {
        // given
        OrderItem cheap = orderItem("id-cheap", 5, 10.0);
        OrderItem expensive = orderItem("id-expensive", 5, 300.0);
        OrderItem mid = orderItem("id-mid", 5, 50.0);
        stubScan(cheap, expensive, mid);

        // when
        List<OrderItem> sorted = repository.scanAndSort(new DynamoDBScanExpression());

        // then
        assertThat(sorted).extracting(OrderItem::getItemId).containsExactly("id-expensive", "id-mid", "id-cheap");
    }

    @Test
    @DisplayName("scanAndSort keeps product, service and delivery bands ordered regardless of input order")
    void scanAndSortKeepsBandsOrdered() {
        // given
        OrderItem delivery = orderItem("id-delivery", PositionGroup.DELIVERY_POSITION);
        OrderItem service = orderItem("id-service", PositionGroup.SERVICE_GROUP_START + 5);
        OrderItem product = orderItem("id-product", 5);
        stubScan(delivery, service, product);

        // when
        List<OrderItem> sorted = repository.scanAndSort(new DynamoDBScanExpression());

        // then
        assertThat(sorted).extracting(OrderItem::getItemId).containsExactly("id-product", "id-service", "id-delivery");
    }

    @Test
    @SuppressWarnings("unchecked")
    void readsTheItemsOfEachOrderByQueryingItsPartition() {
        // given: one stub answering by the queried partition (two when() calls with different matchers would trip
        // the strict stubs of MockitoExtension)
        OrderItem late = orderItem("i-2", 2);
        OrderItem early = orderItem("i-1", 0);
        Map<String, PaginatedQueryList<OrderItem>> results = Map.of("order-1", queryResult(late, early), "order-2", queryResult());
        when(dynamoDBMapper.query(eq(OrderItem.class), any(DynamoDBQueryExpression.class))).thenAnswer(invocation ->
                results.get(((DynamoDBQueryExpression<OrderItem>) invocation.getArgument(1))
                        .getExpressionAttributeValues().get(":orderId").getS()));

        // when
        Map<String, List<OrderItem>> items = repository.findByOrderIds(List.of("order-1", "order-2"));

        // then: one query per order on its own partition, sorted like the scan, never a scan of the table
        assertThat(items.keySet()).containsExactly("order-1", "order-2");
        assertThat(items.get("order-1")).extracting(OrderItem::getItemId).containsExactly("i-1", "i-2");
        assertThat(items.get("order-2")).isEmpty();
        ArgumentCaptor<DynamoDBQueryExpression<OrderItem>> queries = ArgumentCaptor.forClass(DynamoDBQueryExpression.class);
        verify(dynamoDBMapper, times(2)).query(eq(OrderItem.class), queries.capture());
        assertThat(queries.getAllValues()).extracting(DynamoDBQueryExpression::getKeyConditionExpression)
                .containsOnly("orderId = :orderId");
        verify(dynamoDBMapper, never()).scan(eq(OrderItem.class), any(DynamoDBScanExpression.class));
    }

    private static PaginatedQueryList<OrderItem> queryResult(OrderItem... items) {
        @SuppressWarnings("unchecked")
        PaginatedQueryList<OrderItem> result = mock(PaginatedQueryList.class);
        when(result.stream()).thenReturn(List.of(items).stream());
        return result;
    }

    private void stubScan(OrderItem... items) {
        @SuppressWarnings("unchecked")
        PaginatedScanList<OrderItem> scanResult = mock(PaginatedScanList.class);
        when(scanResult.stream()).thenReturn(List.of(items).stream());
        when(dynamoDBMapper.scan(eq(OrderItem.class), any(DynamoDBScanExpression.class))).thenReturn(scanResult);
    }

    private OrderItem orderItem(String itemId, int position) {
        return orderItem(itemId, position, 10.0);
    }

    private OrderItem orderItem(String itemId, int position, double price) {
        OrderItem orderItem = new OrderItem("order-1", "Laptops", "Product", 1, price, "sku", false, position);
        orderItem.setItemId(itemId);
        return orderItem;
    }
}
