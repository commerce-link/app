package pl.commercelink.orders;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pl.commercelink.baskets.BasketItem;
import pl.commercelink.taxonomy.ProductCategory;

import static org.assertj.core.api.Assertions.assertThat;

class OrderItemTest {

    private static final String ORDER_ID = "order-1";

    @Test
    @DisplayName("fromBasketItem copies position from the basket item")
    void fromBasketItemCopiesPositionFromBasketItem() {
        // given
        BasketItem basketItem = basketItem("MFN-1");
        basketItem.setPosition(4);

        // when
        OrderItem orderItem = OrderItem.fromBasketItem(ORDER_ID, basketItem);

        // then
        assertThat(orderItem.getPosition()).isEqualTo(4);
    }

    @Test
    @DisplayName("fromBasketItem leaves position null when the basket item has none")
    void fromBasketItemLeavesPositionNullWhenBasketItemHasNone() {
        // when
        OrderItem orderItem = OrderItem.fromBasketItem(ORDER_ID, basketItem("MFN-1"));

        // then
        assertThat(orderItem.getPosition()).isNull();
    }

    @Test
    @DisplayName("copyWithNewQty copies position from the source item")
    void copyWithNewQtyCopiesPositionFromSourceItem() {
        // given
        OrderItem source = new OrderItem(ORDER_ID, ProductCategory.Laptops, "Product", 1, 100.0, "MFN-1", false);
        source.setPosition(3);

        // when
        OrderItem copy = source.copyWithNewQty(5);

        // then
        assertThat(copy.getPosition()).isEqualTo(3);
    }

    private BasketItem basketItem(String mfn) {
        return new BasketItem("pim-1", "Product", mfn,
                ProductCategory.Laptops, 100.0, 0, 1, null, 3, false);
    }
}
