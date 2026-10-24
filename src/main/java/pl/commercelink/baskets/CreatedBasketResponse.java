package pl.commercelink.baskets;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToDoubleFunction;

public record CreatedBasketResponse(String id, String invoiceNo, List<Item> items, String discountCode,
                                    double discountAmount, double totalPrice, double discountedTotalPrice) {

    public record Item(String itemId, String catalogId, long qty, double unitPrice, double discountedUnitPrice) {
    }

    static Map<BasketItem, Double> listPrices(Basket basket) {
        Map<BasketItem, Double> prices = new IdentityHashMap<>();
        basket.getBasketItems().forEach(item -> prices.put(item, item.getUnitPrice()));
        return prices;
    }

    static CreatedBasketResponse of(Basket basket, Map<BasketItem, Double> listPrices, String invoiceNo) {
        List<Item> items = basket.getBasketItems().stream()
                .map(i -> new Item(i.getId(), i.getCatalogId(), i.getQty(), listPrices.get(i), i.getUnitPrice()))
                .toList();
        return new CreatedBasketResponse(basket.getBasketId(), invoiceNo, items, basket.getDiscountCode(),
                basket.getDiscountAmount(), total(items, Item::unitPrice), total(items, Item::discountedUnitPrice));
    }

    private static double total(List<Item> items, ToDoubleFunction<Item> unitPrice) {
        return items.stream()
                .map(i -> BigDecimal.valueOf(unitPrice.applyAsDouble(i)).multiply(BigDecimal.valueOf(i.qty())))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }
}
