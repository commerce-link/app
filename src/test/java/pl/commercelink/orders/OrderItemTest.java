package pl.commercelink.orders;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pl.commercelink.baskets.BasketItem;
import pl.commercelink.taxonomy.ProductCategory;

import java.util.List;

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
        OrderItem source = orderItem("MFN-1");
        source.setPosition(3);

        // when
        OrderItem copy = source.copyWithNewQty(5);

        // then
        assertThat(copy.getPosition()).isEqualTo(3);
    }

    @Test
    @DisplayName("fillMissingPositions assigns list index only to items without a position")
    void fillMissingPositionsAssignsListIndexOnlyToItemsWithoutPosition() {
        // given
        OrderItem positioned = orderItem("MFN-1");
        positioned.setPosition(9);
        OrderItem missingFirst = orderItem("MFN-2");
        OrderItem missingLast = orderItem("MFN-3");
        List<OrderItem> orderItems = List.of(missingFirst, positioned, missingLast);

        // when
        OrderItem.fillMissingPositions(orderItems);

        // then
        assertThat(orderItems).extracting(OrderItem::getPosition).containsExactly(0, 9, 2);
    }

    @Test
    @DisplayName("nextPositionFor returns highest existing position plus one ignoring items without position")
    void nextPositionForReturnsHighestExistingPositionPlusOne() {
        // given
        OrderItem first = orderItem("MFN-1");
        first.setPosition(0);
        OrderItem second = orderItem("MFN-2");
        second.setPosition(5);
        OrderItem legacy = orderItem("MFN-3");

        // when
        int nextPosition = OrderItem.nextPositionFor(List.of(first, second, legacy));

        // then
        assertThat(nextPosition).isEqualTo(6);
    }

    @Test
    @DisplayName("nextPositionFor falls back to list size when no item has a position")
    void nextPositionForFallsBackToListSizeWhenNoItemHasPosition() {
        // when
        int nextPosition = OrderItem.nextPositionFor(List.of(orderItem("MFN-1"), orderItem("MFN-2")));

        // then
        assertThat(nextPosition).isEqualTo(2);
    }

    @Test
    @DisplayName("nextPositionFor returns zero for an empty list")
    void nextPositionForReturnsZeroForEmptyList() {
        // when
        int nextPosition = OrderItem.nextPositionFor(List.of());

        // then
        assertThat(nextPosition).isEqualTo(0);
    }

    private BasketItem basketItem(String mfn) {
        return new BasketItem("pim-1", "Product", mfn,
                ProductCategory.Laptops, 100.0, 0, 1, null, 3, false);
    }

    private OrderItem orderItem(String mfn) {
        return new OrderItem(ORDER_ID, ProductCategory.Laptops, "Product", 1, 100.0, mfn, false);
    }
}
