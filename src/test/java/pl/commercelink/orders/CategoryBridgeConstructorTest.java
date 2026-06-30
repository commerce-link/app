package pl.commercelink.orders;

import org.junit.jupiter.api.Test;
import pl.commercelink.baskets.BasketItem;
import pl.commercelink.taxonomy.ProductCategory;
import pl.commercelink.warehouse.builtin.WarehouseItem;

import static org.assertj.core.api.Assertions.assertThat;

class CategoryBridgeConstructorTest {

    @Test
    void orderItemStringConstructorStoresCategoryKeyVerbatim() {
        // given / when
        OrderItem item = new OrderItem("order-1", "Laptops", "name", 1, 10.0, "sku", false);

        // then
        assertThat(item.getCategoryKey()).isEqualTo("Laptops");
    }

    @Test
    void orderItemEnumBridgeConstructorStoresEnumName() {
        // given
        ProductCategory category = ProductCategory.Services;

        // when
        OrderItem viaEnum = new OrderItem("order-1", category, "name", 1, 10.0, "sku", false);
        OrderItem viaString = new OrderItem("order-1", category.name(), "name", 1, 10.0, "sku", false);

        // then
        assertThat(viaEnum.getCategoryKey())
                .isEqualTo(category.name())
                .isEqualTo(viaString.getCategoryKey());
    }

    @Test
    void orderItemEnumBridgeConstructorKeepsNullCategoryKey() {
        // given / when
        OrderItem item = new OrderItem("order-1", (ProductCategory) null, "name", 1, 10.0, "sku", false);

        // then
        assertThat(item.getCategoryKey()).isNull();
    }

    @Test
    void basketItemStringConstructorStoresCategoryKeyVerbatim() {
        // given / when
        BasketItem item = new BasketItem("id", "name", "mfn", "Laptops", 10.0, 5.0, 1, "cat", 1, false);

        // then
        assertThat(item.getCategoryKey()).isEqualTo("Laptops");
    }

    @Test
    void basketItemEnumBridgeConstructorStoresEnumName() {
        // given
        ProductCategory category = ProductCategory.Services;

        // when
        BasketItem viaEnum = new BasketItem("id", "name", "mfn", category, 10.0, 5.0, 1, "cat", 1, false);
        BasketItem viaString = new BasketItem("id", "name", "mfn", category.name(), 10.0, 5.0, 1, "cat", 1, false);

        // then
        assertThat(viaEnum.getCategoryKey())
                .isEqualTo(category.name())
                .isEqualTo(viaString.getCategoryKey());
    }

    @Test
    void basketItemEnumBridgeConstructorKeepsNullCategoryKey() {
        // given / when
        BasketItem item = new BasketItem("id", "name", "mfn", (ProductCategory) null, 10.0, 5.0, 1, "cat", 1, false);

        // then
        assertThat(item.getCategoryKey()).isNull();
    }

    @Test
    void warehouseItemStringConstructorStoresCategoryKeyVerbatim() {
        // given / when
        WarehouseItem item = new WarehouseItem("store-1", "delivery-1", "Laptops", "name", "ean", "mfn", 5.0, 1);

        // then
        assertThat(item.getCategoryKey()).isEqualTo("Laptops");
    }

    @Test
    void warehouseItemEnumBridgeConstructorStoresEnumName() {
        // given
        ProductCategory category = ProductCategory.Other;

        // when
        WarehouseItem viaEnum = new WarehouseItem("store-1", "delivery-1", category, "name", "ean", "mfn", 5.0, 1);
        WarehouseItem viaString = new WarehouseItem("store-1", "delivery-1", category.name(), "name", "ean", "mfn", 5.0, 1);

        // then
        assertThat(viaEnum.getCategoryKey())
                .isEqualTo(category.name())
                .isEqualTo(viaString.getCategoryKey());
    }

    @Test
    void warehouseItemEnumBridgeConstructorKeepsNullCategoryKey() {
        // given / when
        WarehouseItem item = new WarehouseItem("store-1", "delivery-1", (ProductCategory) null, "name", "ean", "mfn", 5.0, 1);

        // then
        assertThat(item.getCategoryKey()).isNull();
    }
}
