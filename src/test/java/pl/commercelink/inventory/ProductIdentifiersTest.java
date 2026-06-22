package pl.commercelink.inventory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.taxonomy.TaxonomyCache;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class ProductIdentifiersTest {

    @Mock
    private TaxonomyCache taxonomyCache;
    @Mock
    private SupplierRegistry supplierRegistry;

    private MatchedInventory group(InventoryKey key) {
        return new MatchedInventory(key, taxonomyCache, supplierRegistry);
    }

    @Test
    void containsWhenKeySharesEan() {
        // given
        ProductIdentifiers identifiers = ProductIdentifiers.of(List.of(group(new InventoryKey("5901234567890", "MFN-1"))));

        // when / then
        assertThat(identifiers.contains(new InventoryKey("5901234567890", "OTHER"))).isTrue();
    }

    @Test
    void containsWhenKeySharesProductCode() {
        // given
        ProductIdentifiers identifiers = ProductIdentifiers.of(List.of(group(new InventoryKey("5901234567890", "MFN-1"))));

        // when / then
        assertThat(identifiers.contains(new InventoryKey("0000000000000", "MFN-1"))).isTrue();
    }

    @Test
    void containsWhenKeySharesOnlyPimId() {
        // given
        ProductIdentifiers identifiers = ProductIdentifiers.of(List.of(group(new InventoryKey("PIM-1"))));

        // when / then
        assertThat(identifiers.contains(new InventoryKey("PIM-1"))).isTrue();
    }

    @Test
    void doesNotContainKeyWithNoSharedIdentifier() {
        // given
        ProductIdentifiers identifiers = ProductIdentifiers.of(List.of(group(new InventoryKey("5901234567890", "MFN-1"))));

        // when / then
        assertThat(identifiers.contains(new InventoryKey("0000000000000", "OTHER"))).isFalse();
    }
}
