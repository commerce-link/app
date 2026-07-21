package pl.commercelink.orders;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pl.commercelink.baskets.BasketItem;
import pl.commercelink.stores.DeliveryOption;

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
    @DisplayName("fromDeliveryOption creates a service item without any category string")
    void fromDeliveryOptionCreatesServiceItemWithoutCategory() {
        // given
        DeliveryOption option = new DeliveryOption();
        option.setName("Kurier DPD");
        option.setPrice(20.0);

        // when
        OrderItem deliveryItem = OrderItem.fromDeliveryOption(ORDER_ID, option);

        // then
        assertThat(deliveryItem.isService()).isTrue();
        assertThat(deliveryItem.getCategory()).isNull();
        assertThat(deliveryItem.getPosition()).isEqualTo(PositionGroup.DELIVERY_POSITION);
        assertThat(deliveryItem.getStatus()).isEqualTo(FulfilmentStatus.Delivered);
        assertThat(deliveryItem.getDeliveryId()).isEqualTo(OrderItem.GENERIC_WAREHOUSE_ORDER_NO);
    }

    @Test
    @DisplayName("fromBasketItem marks a service item as warehouse-fulfilled on entry")
    void fromBasketItemMarksServiceItemAsDelivered() {
        // given
        BasketItem basketItem = new BasketItem("pim-1", "Montaż PC", "MONTAZ-1", "Usługi dodatkowe", 150, 100, 1, "cat-1", 801, false);
        basketItem.setService(true);

        // when
        OrderItem orderItem = OrderItem.fromBasketItem(ORDER_ID, basketItem);

        // then
        assertThat(orderItem.isService()).isTrue();
        assertThat(orderItem.getStatus()).isEqualTo(FulfilmentStatus.Delivered);
        assertThat(orderItem.getDeliveryId()).isEqualTo(OrderItem.GENERIC_WAREHOUSE_ORDER_NO);
    }

    @Test
    @DisplayName("fromBasketItem leaves a product item untouched by the service invariant")
    void fromBasketItemLeavesProductItemNew() {
        // given
        BasketItem basketItem = basketItem("MFN-1");

        // when
        OrderItem orderItem = OrderItem.fromBasketItem(ORDER_ID, basketItem);

        // then
        assertThat(orderItem.isService()).isFalse();
        assertThat(orderItem.getStatus()).isEqualTo(FulfilmentStatus.New);
        assertThat(orderItem.getDeliveryId()).isNull();
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
    @DisplayName("constructor with position stores the provided position on the item")
    void constructorWithPositionStoresProvidedPosition() {
        // when
        OrderItem orderItem = new OrderItem(ORDER_ID, "Laptops", "Product", 1, 100.0, "MFN-1", false, 7);

        // then
        assertThat(orderItem.getPosition()).isEqualTo(7);
    }

    @Test
    @DisplayName("fromBasketItem copies the service flag from a service-flagged basket item")
    void fromBasketItemCopiesServiceFlag() {
        // given
        BasketItem basketItem = new BasketItem("pim-1", "Montaż komputera", "MFN-S",
                "Usługi dodatkowe", 100.0, 0, 1, null, 3, false);
        basketItem.setService(true);

        // when
        OrderItem orderItem = OrderItem.fromBasketItem(ORDER_ID, basketItem);

        // then
        assertThat(orderItem.isService()).isTrue();
    }

    @Test
    @DisplayName("copy constructor keeps the service flag of the source item")
    void copyConstructorKeepsServiceFlag() {
        // given
        OrderItem source = new OrderItem(ORDER_ID, "Usługi dodatkowe", "Montaż komputera", 1, 100.0, "MFN-S", false);
        source.setService(true);

        // when
        OrderItem copy = new OrderItem("order-2", source, 1);

        // then
        assertThat(copy.isService()).isTrue();
    }

    @Test
    @DisplayName("hasSupplierAllocation is false for a warehouse-fulfilled service item")
    void hasSupplierAllocationIsFalseForWarehouseFulfilledService() {
        // given
        OrderItem orderItem = orderItem("MFN-1");
        orderItem.setService(true);

        // when
        orderItem.markAsWarehouseFulfilled();

        // then
        assertThat(orderItem.hasSupplierAllocation()).isFalse();
    }

    @Test
    @DisplayName("hasSupplierAllocation is true for an ordered product with real allocation details")
    void hasSupplierAllocationIsTrueForOrderedProductWithAllocationDetails() {
        // given
        OrderItem orderItem = orderItem("MFN-1");
        orderItem.setEan("EAN-1");
        orderItem.setManufacturerCode("MFN-1");
        orderItem.setDeliveryId("delivery-1");
        orderItem.setStatus(FulfilmentStatus.Ordered);

        // then
        assertThat(orderItem.hasSupplierAllocation()).isTrue();
    }

    private BasketItem basketItem(String mfn) {
        return new BasketItem("pim-1", "Product", mfn,
                "Laptops", 100.0, 0, 1, null, 3, false);
    }

    private OrderItem orderItem(String mfn) {
        return new OrderItem(ORDER_ID, "Laptops", "Product", 1, 100.0, mfn, false);
    }
}
