package pl.commercelink.migration;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.RequiredArgsConstructor;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.orders.OrderItemsRepository;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static pl.commercelink.starter.migration.DynamoDbMigrationSupport.executeUpdate;

@ChangeUnit(id = "V005-backfill-order-item-positions", order = "005", author = "commercelink")
@RequiredArgsConstructor
public class V005_BackfillOrderItemPositions {

    private static final String TABLE_NAME = "OrderItems";
    private static final String UPDATE_EXPRESSION = "SET #p = if_not_exists(#p, :position)";
    private static final Map<String, String> EXPRESSION_ATTRIBUTE_NAMES = Map.of("#p", "position");

    private final OrderItemsRepository orderItemsRepository;
    private final AmazonDynamoDB dynamoDB;

    @Execution
    public void backfillPositions() {
        Map<String, List<OrderItem>> itemsByOrderId = orderItemsRepository.findAll().stream()
                .collect(Collectors.groupingBy(OrderItem::getOrderId));

        for (List<OrderItem> orderItems : itemsByOrderId.values()) {
            List<OrderItem> displayOrderedItems = orderItems.stream()
                    .sorted(Comparator.comparingInt(V005_BackfillOrderItemPositions::displaySequence)
                            .thenComparing(OrderItem::getItemId))
                    .toList();

            for (int position = 0; position < displayOrderedItems.size(); position++) {
                OrderItem orderItem = displayOrderedItems.get(position);
                if (orderItem.getPosition() == null) {
                    backfillPosition(orderItem, position);
                }
            }
        }
    }

    private void backfillPosition(OrderItem orderItem, int position) {
        Map<String, AttributeValue> key = Map.of(
                "orderId", new AttributeValue().withS(orderItem.getOrderId()),
                "itemId", new AttributeValue().withS(orderItem.getItemId()));

        executeUpdate(dynamoDB, TABLE_NAME, key, UPDATE_EXPRESSION, EXPRESSION_ATTRIBUTE_NAMES,
                Map.of(":position", new AttributeValue().withN(String.valueOf(position))));
    }

    private static int displaySequence(OrderItem orderItem) {
        if (orderItem.getCategoryKey() == null) {
            return Integer.MAX_VALUE;
        }
        try {
            return orderItem.getSequenceNumber();
        } catch (IllegalArgumentException e) {
            return Integer.MAX_VALUE;
        }
    }

    @RollbackExecution
    public void rollback() {
    }
}
