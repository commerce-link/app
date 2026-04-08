package pl.commercelink.invoicing;

import pl.commercelink.baskets.BasketItem;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.taxonomy.ProductCategory;

import java.util.List;
import java.util.stream.Collectors;

public class InvoicePositionName {

    public static String fromBasketItems(List<BasketItem> basketItems) {
        return fromOrderItems("", basketItems.stream()
                .map(b -> OrderItem.fromBasketItem(null, b))
                .collect(Collectors.toList())
        );
    }

    public static String fromOrderItems(String prefix, List<OrderItem> orderItems) {
        return prefix + " " +
                orderItems.stream()
                        .filter(i -> i.getCategory() != ProductCategory.Services)
                        .map(i -> formatName(i.getName(), i.getQty()))
                        .collect(Collectors.joining(", "))
                        .trim();
    }

    private static String formatName(String name, int qty) {
        return qty == 1 ? name.trim() : qty + "x " + name.trim();
    }

}
