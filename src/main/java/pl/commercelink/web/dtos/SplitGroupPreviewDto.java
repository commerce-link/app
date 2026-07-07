package pl.commercelink.web.dtos;

import pl.commercelink.orders.GroupSku;
import pl.commercelink.orders.OrderItem;

import java.util.List;
import java.util.function.Function;

public record SplitGroupPreviewDto(String name, String sku, int qty, double price, double totalPrice,
                                   List<Component> components) {

    public record Component(int qty, String sku, String name) {
    }

    public static SplitGroupPreviewDto from(OrderItem item, Function<String, String> nameResolver) {
        List<Component> components = GroupSku.parse(item.getSku()).stream()
                .map(c -> new Component(c.qty() * item.getQty(), c.sku(), nameResolver.apply(c.sku())))
                .toList();

        return new SplitGroupPreviewDto(
                item.getName(),
                item.getSku(),
                item.getQty(),
                item.getPrice(),
                item.getTotalPrice(),
                components
        );
    }

}
