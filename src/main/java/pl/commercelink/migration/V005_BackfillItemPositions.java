package pl.commercelink.migration;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.RequiredArgsConstructor;
import pl.commercelink.baskets.Basket;
import pl.commercelink.baskets.BasketsRepository;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.orders.OrderItemsRepository;
import pl.commercelink.taxonomy.Positioned;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@ChangeUnit(id = "V005-backfill-item-positions", order = "005", author = "commercelink")
@RequiredArgsConstructor
public class V005_BackfillItemPositions {

    private final OrderItemsRepository orderItemsRepository;
    private final BasketsRepository basketsRepository;

    @Execution
    public void backfillPositions() {
        backfillOrderItemPositions();
        backfillBasketItemPositions();
    }

    private void backfillOrderItemPositions() {
        Map<String, List<OrderItem>> itemsByOrderId = orderItemsRepository.findAll().stream()
                .collect(Collectors.groupingBy(OrderItem::getOrderId));

        for (List<OrderItem> orderItems : itemsByOrderId.values()) {
            List<OrderItem> displayOrderedItems = orderItems.stream()
                    .sorted(Comparator.comparingInt(V005_BackfillItemPositions::displaySequence))
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

    private void backfillBasketItemPositions() {
        for (Basket basket : basketsRepository.findAll()) {
            if (Positioned.fillMissing(basket.getBasketItems())) {
                basketsRepository.save(basket);
            }
        }
    }

    @RollbackExecution
    public void rollback() {
    }
}
