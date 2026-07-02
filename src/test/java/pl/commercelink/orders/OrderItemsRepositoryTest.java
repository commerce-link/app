package pl.commercelink.orders;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBScanExpression;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ScanRequest;
import com.amazonaws.services.dynamodbv2.model.ScanResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderItemsRepositoryTest {

    @Mock
    private AmazonDynamoDB amazonDynamoDB;

    @InjectMocks
    private OrderItemsRepository orderItemsRepository;

    @Test
    @DisplayName("scanAndSort orders items by position ascending")
    void scanAndSortOrdersItemsByPositionAscending() {
        // given
        givenScannedItems(List.of(
                orderItemAttributes("item-a", 2),
                orderItemAttributes("item-b", 0),
                orderItemAttributes("item-c", 1)));

        // when
        List<OrderItem> result = orderItemsRepository.scanAndSort(new DynamoDBScanExpression());

        // then
        assertThat(result).extracting(OrderItem::getItemId).containsExactly("item-b", "item-c", "item-a");
    }

    @Test
    @DisplayName("scanAndSort puts items with null position last")
    void scanAndSortPutsItemsWithNullPositionLast() {
        // given
        givenScannedItems(List.of(
                orderItemAttributes("item-a", null),
                orderItemAttributes("item-b", 1),
                orderItemAttributes("item-c", 0)));

        // when
        List<OrderItem> result = orderItemsRepository.scanAndSort(new DynamoDBScanExpression());

        // then
        assertThat(result).extracting(OrderItem::getItemId).containsExactly("item-c", "item-b", "item-a");
    }

    @Test
    @DisplayName("scanAndSort breaks duplicate positions by item id")
    void scanAndSortBreaksDuplicatePositionsByItemId() {
        // given
        givenScannedItems(List.of(
                orderItemAttributes("item-b", 0),
                orderItemAttributes("item-c", 0),
                orderItemAttributes("item-a", 0)));

        // when
        List<OrderItem> result = orderItemsRepository.scanAndSort(new DynamoDBScanExpression());

        // then
        assertThat(result).extracting(OrderItem::getItemId).containsExactly("item-a", "item-b", "item-c");
    }

    @Test
    @DisplayName("scanAndSort breaks null position ties by item id")
    void scanAndSortBreaksNullPositionTiesByItemId() {
        // given
        givenScannedItems(List.of(
                orderItemAttributes("item-z", null),
                orderItemAttributes("item-a", null),
                orderItemAttributes("item-b", 0)));

        // when
        List<OrderItem> result = orderItemsRepository.scanAndSort(new DynamoDBScanExpression());

        // then
        assertThat(result).extracting(OrderItem::getItemId).containsExactly("item-b", "item-a", "item-z");
    }

    private void givenScannedItems(List<Map<String, AttributeValue>> items) {
        when(amazonDynamoDB.scan(any(ScanRequest.class))).thenReturn(new ScanResult().withItems(items));
    }

    private Map<String, AttributeValue> orderItemAttributes(String itemId, Integer position) {
        Map<String, AttributeValue> attributes = new HashMap<>();
        attributes.put("orderId", new AttributeValue().withS("order-1"));
        attributes.put("itemId", new AttributeValue().withS(itemId));
        if (position != null) {
            attributes.put("position", new AttributeValue().withN(String.valueOf(position)));
        }
        return attributes;
    }

}
