package pl.commercelink.web.dtos;

import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderItem;

import java.util.List;

public record OrderWithItemsDto(
        String orderId,
        String status,
        String fulfilmentType,
        List<OrderItemDto> items
) {

    public record OrderItemDto(
            String itemId,
            String category,
            String name,
            String sku,
            String ean,
            String mfn,
            double priceGross,
            double costGross,
            String status
    ) {

        public static OrderItemDto from(OrderItem item) {
            return new OrderItemDto(
                    item.getItemId(),
                    item.getCategory(),
                    item.getName(),
                    item.getSku(),
                    item.getEan(),
                    item.getManufacturerCode(),
                    item.getPrice(),
                    item.unitCost().grossValue(),
                    item.getStatus().name()
            );
        }
    }

    public static OrderWithItemsDto from(Order order, List<OrderItem> items) {
        return new OrderWithItemsDto(
                order.getOrderId(),
                order.getStatus().name(),
                order.getFulfilmentType() == null ? null : order.getFulfilmentType().name(),
                items.stream().map(OrderItemDto::from).toList()
        );
    }
}
