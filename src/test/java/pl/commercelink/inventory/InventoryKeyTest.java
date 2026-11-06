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
        assertThat(key.getProductCodes()).containsExactlyInAnyOrder("MFN-1", "MFN-2");
        assertThat(key.matches(new InventoryKey("5901234567890", null))).isTrue();
    }

    @Test
    void sameCodeWithAnotherEanIsAnotherProduct() {
        // given
        InventoryKey size42 = new InventoryKey("5901000000042", "SHOE-1");

        // when / then
        assertThat(size42.matches(new InventoryKey("5901000000043", "SHOE-1"))).isFalse();
    }

    @Test
    void sameEanOrPimIdIsTheSameProduct() {
        // given
        InventoryKey key = InventoryKey.fromPimIdAndEan("P1", "5901000000001");

        // when / then
        assertThat(key.matches(new InventoryKey("5901000000001", "OTHER-1"))).isTrue();
        assertThat(key.matches(InventoryKey.fromPimIdAndEan("P1", "5903000000003"))).isTrue();
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
