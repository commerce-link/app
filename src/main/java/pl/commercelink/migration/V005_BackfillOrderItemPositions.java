package pl.commercelink.migration;

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

@ChangeUnit(id = "V005-backfill-order-item-positions", order = "005", author = "commercelink")
@RequiredArgsConstructor
public class V005_BackfillOrderItemPositions {

    private final OrderItemsRepository orderItemsRepository;

    @Execution
    public void backfillPositions() {
        Map<String, List<OrderItem>> itemsByOrderId = orderItemsRepository.findAll().stream()
                .collect(Collectors.groupingBy(OrderItem::getOrderId));

        for (List<OrderItem> orderItems : itemsByOrderId.values()) {
            List<OrderItem> displayOrderedItems = orderItems.stream()
                    .sorted(Comparator.comparingInt(V005_BackfillOrderItemPositions::displaySequence))
                    .toList();

            for (int position = 0; position < displayOrderedItems.size(); position++) {
                OrderItem orderItem = displayOrderedItems.get(position);
                if (orderItem.getPosition() == null) {
                    orderItem.setPosition(position);
                    orderItemsRepository.save(orderItem);
                }
            }
        }
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
