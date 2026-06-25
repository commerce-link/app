package pl.commercelink.orders.notifications;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AccessLevel;
import lombok.Getter;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.starter.localization.EnumLocalizer;

@Getter(AccessLevel.PACKAGE)
class LocalizedOrderItem {

    @JsonProperty("name")
    private String name;
    @JsonProperty("quantity")
    private int quantity;
    @JsonProperty("price")
    private double price;

    static LocalizedOrderItem fromOrderItem(OrderItem orderItem, EnumLocalizer enumLocalizer) {
        LocalizedOrderItem localizedOrderItem = new LocalizedOrderItem();
        localizedOrderItem.name = orderItem.getName();
        localizedOrderItem.quantity = orderItem.getQty();
        localizedOrderItem.price = orderItem.getPrice();
        return localizedOrderItem;
    }
}
