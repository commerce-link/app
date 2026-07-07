package pl.commercelink.orders;

import org.junit.jupiter.api.Test;
import pl.commercelink.baskets.BasketItem;
import pl.commercelink.warehouse.builtin.WarehouseItem;

import static org.assertj.core.api.Assertions.assertThat;

class CategoryConstructorTest {

    @Test
    void orderItemConstructorStoresCategoryVerbatim() {
        // given / when
        OrderItem item = new OrderItem("order-1", "Laptops", "name", 1, 10.0, "sku", false);

        // then
        assertThat(item.getCategory()).isEqualTo("Laptops");
    }

    @Test
    void basketItemConstructorStoresCategoryVerbatim() {
        // given / when
        BasketItem item = new BasketItem("id", "name", "mfn", "Laptops", 10.0, 5.0, 1, "cat", 1, false);

        // then
        assertThat(item.getCategory()).isEqualTo("Laptops");
    }

    @Test
    void warehouseItemConstructorStoresCategoryVerbatim() {
        // given / when
        WarehouseItem item = new WarehouseItem("store-1", "delivery-1", "Laptops", "name", "ean", "mfn", 5.0, 1);

        // then
        assertThat(item.getCategory()).isEqualTo("Laptops");
    }
}
