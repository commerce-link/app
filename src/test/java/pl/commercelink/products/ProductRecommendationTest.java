package pl.commercelink.products;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.inventory.InventoryKey;
import pl.commercelink.inventory.MatchedInventory;
import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.inventory.supplier.api.InventoryItem;
import pl.commercelink.pim.api.PimEntry;
import pl.commercelink.pim.api.PimIdentifier;
import pl.commercelink.pim.api.PimIdentifierType;
import pl.commercelink.taxonomy.Taxonomy;
import pl.commercelink.taxonomy.TaxonomyCache;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductRecommendationTest {

    private static final Taxonomy TAXONOMY = new Taxonomy("5900000000009", "ABC1", "Other Brand", "Other product",
            "Monitory", 5, null, null);

    @Mock
    private TaxonomyCache taxonomyCache;

    @Mock
    private SupplierRegistry supplierRegistry;

    @Test
    void eanAndCodeComeFromThePimEntryWhenThereIsOne() {
        // given
        MatchedInventory group = group(offer("5900000000009", "ABC1"), offer("5900000000016", "ABC1-EU"));
        PimEntry entry = entry(gtin("5900000000016", true), mpn("ABC1-EU", true));

        // when
        Product product = new ProductRecommendation(new CategoryDefinition(), group, Optional.of(entry)).toProduct();

        // then
        assertThat(product.getEan()).isEqualTo("5900000000016");
        assertThat(product.getManufacturerCode()).isEqualTo("ABC1-EU");
        assertThat(product.getBrand()).isEqualTo("Brand");
    }

    @Test
    void missingPimCodesFallBackToTheTaxonomy() {
        // given
        MatchedInventory group = group(offer("5900000000009", "ABC1"));
        PimEntry entry = entry();

        // when
        Product product = new ProductRecommendation(new CategoryDefinition(), group, Optional.of(entry)).toProduct();

        // then
        assertThat(product.getEan()).isEqualTo("5900000000009");
        assertThat(product.getManufacturerCode()).isEqualTo("ABC1");
    }

    @Test
    void withoutPimEntryEverythingComesFromTheTaxonomy() {
        // given
        MatchedInventory group = group(offer("5900000000009", "ABC1"));

        // when
        Product product = new ProductRecommendation(new CategoryDefinition(), group, Optional.empty()).toProduct();

        // then
        assertThat(product.getEan()).isEqualTo("5900000000009");
        assertThat(product.getManufacturerCode()).isEqualTo("ABC1");
        assertThat(product.getBrand()).isEqualTo("Other Brand");
        assertThat(product.getName()).isEqualTo("Other product");
    }

    private MatchedInventory group(InventoryItem... offers) {
        when(taxonomyCache.find(any(InventoryKey.class))).thenReturn(TAXONOMY);
        return new MatchedInventory(new InventoryKey(), List.of(offers), taxonomyCache, supplierRegistry);
    }

    private static InventoryItem offer(String ean, String mfn) {
        return new InventoryItem(ean, mfn, 100.0, "PLN", 5, 1, "Acme", true);
    }

    private static PimEntry entry(PimIdentifier... identifiers) {
        return new PimEntry("pim-1", List.of(identifiers), "Brand", "Product", "Monitory", null, true, null, null);
    }

    private static PimIdentifier gtin(String value, boolean primary) {
        return new PimIdentifier(value, PimIdentifierType.GTIN, primary);
    }

    private static PimIdentifier mpn(String value, boolean primary) {
        return new PimIdentifier(value, PimIdentifierType.MPN, primary);
    }
}
