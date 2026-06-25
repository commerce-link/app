package pl.commercelink.orders.fulfilment;

import org.junit.jupiter.api.Test;
import pl.commercelink.inventory.supplier.api.InventoryItem;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.taxonomy.ItemType;
import pl.commercelink.taxonomy.ProductCategory;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link FulfilmentSource} no longer carries the {@link ProductCategory} enum copied off the order item.
 * It carries the frozen sort {@code sequenceNumber} and {@link ItemType} so the sort order and the
 * Services-bypass eligibility gate survive without the enum.
 */
class FulfilmentSourceCategorizationTest {

    private static InventoryItem offer() {
        return new InventoryItem("111", "M", 10, "PLN", 1, 1, "Action", true, false, false);
    }

    @Test
    void copiesFrozenSequenceFromOrderItem() {
        // given
        OrderItem cpu = new OrderItem(null, ProductCategory.CPU, "n", 1, 0, "S", false);
        OrderItem laptops = new OrderItem(null, ProductCategory.Laptops, "n", 1, 0, "S", false);

        // when
        FulfilmentSource cpuSource = new FulfilmentSource(cpu, offer());
        FulfilmentSource laptopsSource = new FulfilmentSource(laptops, offer());

        // then
        assertThat(cpuSource.getSequenceNumber()).isEqualTo(0);
        assertThat(laptopsSource.getSequenceNumber()).isEqualTo(ProductCategory.Laptops.ordinal());
    }

    @Test
    void copiesItemTypeFromOrderItem() {
        // given
        OrderItem service = new OrderItem(null, ProductCategory.Services, "n", 1, 0, "S", false);
        OrderItem product = new OrderItem(null, ProductCategory.CPU, "n", 1, 0, "S", false);

        // when / then
        assertThat(new FulfilmentSource(service, offer()).getItemType()).isEqualTo(ItemType.SERVICE);
        assertThat(new FulfilmentSource(product, offer()).getItemType()).isEqualTo(ItemType.PRODUCT);
    }
}
