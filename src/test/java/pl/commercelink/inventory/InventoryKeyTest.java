package pl.commercelink.inventory;

import org.junit.jupiter.api.Test;
import pl.commercelink.products.Product;

import static org.assertj.core.api.Assertions.assertThat;

class InventoryKeyTest {

    @Test
    void mergeAdoptsIdWhenAbsent() {
        // given
        InventoryKey key = new InventoryKey("5901234567890", "MFN-1");

        // when
        key.merge(new InventoryKey("PIM-1"));

        // then
        assertThat(key.getId()).isEqualTo("PIM-1");
    }

    @Test
    void mergeDoesNotOverwriteExistingId() {
        // given
        InventoryKey key = new InventoryKey("PIM-1");

        // when
        key.merge(new InventoryKey("PIM-2"));

        // then
        assertThat(key.getId()).isEqualTo("PIM-1");
    }

    @Test
    void mergeUnionsEansAndProductCodes() {
        // given
        InventoryKey key = new InventoryKey("5901234567890", "MFN-1");

        // when
        key.merge(new InventoryKey("4000000000009", "MFN-2"));

        // then
        assertThat(key.matches(new InventoryKey("4000000000009", null))).isTrue();
        assertThat(key.matches(new InventoryKey(null, "MFN-2"))).isTrue();
        assertThat(key.matches(new InventoryKey("5901234567890", null))).isTrue();
    }

    @Test
    void productKeyCarriesItsPimId() {
        // given
        Product product = new Product("cat-1", "P1", "5901234567890", "MFN-1", "Acme", null, "Product", null);

        // when
        InventoryKey key = InventoryKey.fromProduct(product);

        // then
        assertThat(key.getId()).isEqualTo("P1");
    }
}
