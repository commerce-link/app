package pl.commercelink.orders.notifications;

import com.fasterxml.jackson.annotation.JsonProperty;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.products.ProductCategoryLocalization;

class LocalizedOrderItem {

    @JsonProperty("category")
    private String category;
    @JsonProperty("name")
    private String name;
    @JsonProperty("quantity")
    private int quantity;
    @JsonProperty("price")
    private double price;

    String getCategory() {
        return category;
    }

    String getName() {
        return name;
    }

    int getQuantity() {
        return quantity;
    }

    double getPrice() {
        return price;
    }

    static LocalizedOrderItem fromOrderItem(OrderItem orderItem) {
        LocalizedOrderItem localizedOrderItem = new LocalizedOrderItem();
        localizedOrderItem.category = ProductCategoryLocalization.INSTANCE.singular(orderItem.getCategory());
        localizedOrderItem.name = orderItem.getName();
        localizedOrderItem.quantity = orderItem.getQty();
        localizedOrderItem.price = orderItem.getPrice();
        return localizedOrderItem;
    }
}
