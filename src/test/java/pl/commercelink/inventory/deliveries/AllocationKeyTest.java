package pl.commercelink.inventory.deliveries;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AllocationKeyTest {

    @Test
    void sourceIsNamedByTheWholeEmailOfTheBuyer() {
        // when
        AllocationKey key = new AllocationKey("rvw1-ewa-nowicka", "item-1", "ewa.nowicka1@rv.test");

        // then
        assertThat(key.getName()).isEqualTo("ewa.nowicka1@rv.test");
    }

    @Test
    void sourceWithoutAnEmailIsTheWarehouse() {
        // when
        AllocationKey key = new AllocationKey("order-1", "item-1", " ");

        // then
        assertThat(key.getName()).isEqualTo("Warehouse");
    }
}
