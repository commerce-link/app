package pl.commercelink.migration;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ScanRequest;
import com.amazonaws.services.dynamodbv2.model.ScanResult;
import com.amazonaws.services.dynamodbv2.model.UpdateItemRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.orders.OrderItemsRepository;
import pl.commercelink.taxonomy.ProductCategory;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class V005BackfillOrderItemPositionsTest {

    private static final String ORDER_ID = "order-1";

    @Mock
    private OrderItemsRepository orderItemsRepository;

    @Mock
    private AmazonDynamoDB dynamoDB;

    @InjectMocks
    private V005_BackfillOrderItemPositions migration;

    @Test
    @DisplayName("assigns order item positions following the category ordinal display order within each order")
    void assignsOrderItemPositionsFollowingCategoryOrdinalDisplayOrder() {
        // given
        OrderItem laptopItem = orderItem(ORDER_ID, "item-l", ProductCategory.Laptops.name());
        OrderItem cpuItem = orderItem(ORDER_ID, "item-c", ProductCategory.CPU.name());
        OrderItem serviceItem = orderItem(ORDER_ID, "item-s", ProductCategory.Services.name());
        stubScanReturning(ORDER_ID);
        when(orderItemsRepository.findByOrderId(ORDER_ID)).thenReturn(List.of(laptopItem, cpuItem, serviceItem));

        // when
        migration.backfillPositions();

        // then
        assertThat(capturedPositionsByItemId()).containsExactlyInAnyOrderEntriesOf(Map.of(
                "item-c", 0,
                "item-s", 1,
                "item-l", 2));
    }

    @Test
    @DisplayName("writes positions through if_not_exists update on the item key without touching the version")
    void writesPositionsThroughIfNotExistsUpdateOnItemKey() {
        // given
        OrderItem cpuItem = orderItem(ORDER_ID, "item-c", ProductCategory.CPU.name());
        stubScanReturning(ORDER_ID);
        when(orderItemsRepository.findByOrderId(ORDER_ID)).thenReturn(List.of(cpuItem));

        // when
        migration.backfillPositions();

        // then
        ArgumentCaptor<UpdateItemRequest> captor = ArgumentCaptor.forClass(UpdateItemRequest.class);
        verify(dynamoDB).updateItem(captor.capture());
        UpdateItemRequest request = captor.getValue();
        assertThat(request.getTableName()).isEqualTo("OrderItems");
        assertThat(request.getKey().get("orderId").getS()).isEqualTo(ORDER_ID);
        assertThat(request.getKey().get("itemId").getS()).isEqualTo("item-c");
        assertThat(request.getUpdateExpression()).isEqualTo("SET #p = if_not_exists(#p, :position)");
        assertThat(request.getExpressionAttributeNames()).containsEntry("#p", "position");
        assertThat(request.getExpressionAttributeValues().get(":position").getN()).isEqualTo("0");
    }

    @Test
    @DisplayName("positions items independently per order")
    void positionsItemsIndependentlyPerOrder() {
        // given
        OrderItem firstOrderItem = orderItem("order-1", "item-a", ProductCategory.Laptops.name());
        OrderItem secondOrderItem = orderItem("order-2", "item-b", ProductCategory.CPU.name());
        stubScanReturning("order-1", "order-2");
        when(orderItemsRepository.findByOrderId("order-1")).thenReturn(List.of(firstOrderItem));
        when(orderItemsRepository.findByOrderId("order-2")).thenReturn(List.of(secondOrderItem));

        // when
        migration.backfillPositions();

        // then
        assertThat(capturedPositionsByItemId()).containsExactlyInAnyOrderEntriesOf(Map.of(
                "item-a", 0,
                "item-b", 0));
    }

    @Test
    @DisplayName("treats missing and unknown categories as last without failing")
    void treatsMissingAndUnknownCategoriesAsLastWithoutFailing() {
        // given
        OrderItem cpuItem = orderItem(ORDER_ID, "item-a", ProductCategory.CPU.name());
        OrderItem missingCategoryItem = orderItem(ORDER_ID, "item-b", null);
        OrderItem unknownCategoryItem = orderItem(ORDER_ID, "item-c", "NotACategory");
        stubScanReturning(ORDER_ID);
        when(orderItemsRepository.findByOrderId(ORDER_ID))
                .thenReturn(List.of(missingCategoryItem, cpuItem, unknownCategoryItem));

        // when
        migration.backfillPositions();

        // then
        assertThat(capturedPositionsByItemId()).containsExactlyInAnyOrderEntriesOf(Map.of(
                "item-a", 0,
                "item-b", 1,
                "item-c", 2));
    }

    @Test
    @DisplayName("breaks category ties by itemId so re-runs assign the same positions")
    void breaksCategoryTiesByItemIdSoRerunsAssignSamePositions() {
        // given
        OrderItem laterItem = orderItem(ORDER_ID, "item-b", ProductCategory.CPU.name());
        OrderItem earlierItem = orderItem(ORDER_ID, "item-a", ProductCategory.CPU.name());
        stubScanReturning(ORDER_ID);
        when(orderItemsRepository.findByOrderId(ORDER_ID)).thenReturn(List.of(laterItem, earlierItem));

        // when
        migration.backfillPositions();

        // then
        assertThat(capturedPositionsByItemId()).containsExactlyInAnyOrderEntriesOf(Map.of(
                "item-a", 0,
                "item-b", 1));
    }

    @Test
    @DisplayName("issues an if_not_exists update for every item so existing positions are preserved by DynamoDB")
    void issuesIfNotExistsUpdateForEveryItemPreservingExistingPositions() {
        // given
        OrderItem firstItem = orderItem(ORDER_ID, "item-a", ProductCategory.CPU.name());
        OrderItem secondItem = orderItem(ORDER_ID, "item-b", ProductCategory.CPU.name());
        stubScanReturning(ORDER_ID);
        when(orderItemsRepository.findByOrderId(ORDER_ID)).thenReturn(List.of(firstItem, secondItem));

        // when
        migration.backfillPositions();

        // then
        ArgumentCaptor<UpdateItemRequest> captor = ArgumentCaptor.forClass(UpdateItemRequest.class);
        verify(dynamoDB, times(2)).updateItem(captor.capture());
        assertThat(captor.getAllValues())
                .allSatisfy(request -> assertThat(request.getUpdateExpression())
                        .isEqualTo("SET #p = if_not_exists(#p, :position)"));
        assertThat(capturedPositionsByItemId()).containsExactlyInAnyOrderEntriesOf(Map.of(
                "item-a", 0,
                "item-b", 1));
    }

    private void stubScanReturning(String... orderIds) {
        List<Map<String, AttributeValue>> items = Arrays.stream(orderIds)
                .distinct()
                .map(id -> Map.of("orderId", new AttributeValue().withS(id)))
                .collect(Collectors.toList());
        when(dynamoDB.scan(any(ScanRequest.class))).thenReturn(new ScanResult().withItems(items));
    }

    private Map<String, Integer> capturedPositionsByItemId() {
        ArgumentCaptor<UpdateItemRequest> captor = ArgumentCaptor.forClass(UpdateItemRequest.class);
        verify(dynamoDB, atLeastOnce()).updateItem(captor.capture());
        return captor.getAllValues().stream().collect(Collectors.toMap(
                request -> request.getKey().get("itemId").getS(),
                request -> Integer.parseInt(request.getExpressionAttributeValues().get(":position").getN())));
    }

    private OrderItem orderItem(String orderId, String itemId, String categoryKey) {
        OrderItem item = new OrderItem(orderId, categoryKey, "Product", 1, 100.0, "SKU", false);
        item.setItemId(itemId);
        return item;
    }
}
