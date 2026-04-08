package pl.commercelink.invoicing;

import pl.commercelink.invoicing.api.InvoicePosition;
import pl.commercelink.invoicing.api.Price;
import pl.commercelink.orders.OrderItem;

import java.util.List;

class InvoicePositionConverter {

    static InvoicePosition fromOrderItem(OrderItem orderItem) {
        return new InvoicePosition(null, orderItem.getName(), orderItem.getQty(), Price.fromGross(orderItem.getTotalPrice(), orderItem.getTax()));
    }

   static InvoicePosition fromOrderItems(String name, List<OrderItem> orderItems) {
        if (orderItems.stream().map(OrderItem::getTax).distinct().count() > 1) {
            throw new IllegalArgumentException("All order items must have the same tax rate to be consolidated into a single invoice position.");
        }

        double totalPriceGross = orderItems.stream()
                .mapToDouble(OrderItem::getTotalPrice)
                .sum();

        double tax = orderItems.iterator().next().getTax();

        return new InvoicePosition(
                null,
                InvoicePositionName.fromOrderItems(name, orderItems),
                1,
                Price.fromGross(totalPriceGross, tax)
        );
    }

}
