package pl.commercelink.orders;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBScanExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.PaginatedScanList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
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
    @DisplayName("scanAndSort breaks position ties deterministically by itemId")
    void scanAndSortBreaksPositionTiesByItemId() {
        // given
        OrderItem b = orderItem("id-B", 5);
        OrderItem a = orderItem("id-A", 5);
        OrderItem c = orderItem("id-C", 5);
        stubScan(b, c, a);

        // when
        List<OrderItem> sorted = repository.scanAndSort(new DynamoDBScanExpression());

        // then
        assertThat(sorted).extracting(OrderItem::getItemId).containsExactly("id-A", "id-B", "id-C");
    }

    @Test
    @DisplayName("scanAndSort keeps product, service and delivery bands ordered regardless of input order")
    void scanAndSortKeepsBandsOrdered() {
        // given
        OrderItem delivery = orderItem("id-delivery", PositionBands.DELIVERY_POSITION);
        OrderItem service = orderItem("id-service", PositionBands.SERVICE_BAND_START + 5);
        OrderItem product = orderItem("id-product", 5);
        stubScan(delivery, service, product);

        // when
        List<OrderItem> sorted = repository.scanAndSort(new DynamoDBScanExpression());

        // then
        assertThat(sorted).extracting(OrderItem::getItemId).containsExactly("id-product", "id-service", "id-delivery");
    }

    private void stubScan(OrderItem... items) {
        @SuppressWarnings("unchecked")
        PaginatedScanList<OrderItem> scanResult = mock(PaginatedScanList.class);
        when(scanResult.stream()).thenReturn(List.of(items).stream());
        when(dynamoDBMapper.scan(eq(OrderItem.class), any(DynamoDBScanExpression.class))).thenReturn(scanResult);
    }

    private OrderItem orderItem(String itemId, int position) {
        OrderItem orderItem = new OrderItem("order-1", "Laptops", "Product", 1, 10.0, "sku", false, position);
        orderItem.setItemId(itemId);
        return orderItem;
    }
}
