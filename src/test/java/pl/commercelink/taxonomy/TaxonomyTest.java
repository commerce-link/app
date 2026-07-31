package pl.commercelink.taxonomy;

import org.junit.jupiter.api.Test;
import pl.commercelink.inventory.supplier.api.SupplierProduct;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaxonomyTest {

    @Test
    void constructorUnifiesEanAndMfn() {
        // given / when
        Taxonomy taxonomy = new Taxonomy("0590123412345", "mfn 1", "BrandX", "Laptop Pro", "Laptops", 10, null, null);

        // then
        assertEquals("590123412345", taxonomy.ean());
        assertEquals("MFN1", taxonomy.mfn());
    }

    @Test
    void completeCategorizedTaxonomyIsProcessable() {
        // given
        Taxonomy taxonomy = new Taxonomy("5901234123457", "MFN-1", "BrandX", "Laptop Pro", "Laptops", 10, null, null);

        // when / then
        assertTrue(taxonomy.isProcessable());
    }

    @Test
    void nullCategoryIsNotProcessable() {
        // given
        Taxonomy taxonomy = new Taxonomy("5901234123457", "MFN-1", "BrandX", "Laptop Pro", (String) null, 10, null, null);

        // when / then
        assertFalse(taxonomy.isProcessable());
    }

    @Test
    void categorizedRowWithBlankIdentifierIsNotProcessable() {
        // given
        Taxonomy taxonomy = new Taxonomy("", "", "BrandX", "Laptop Pro", "Laptops", 10, null, null);

        // when / then
        assertFalse(taxonomy.isProcessable());
    }

    @Test
    void fromSupplierProductLeavesCategoryAndCategoryIdNullAndCarriesRawCategory() {
        // given
        SupplierProduct product = new SupplierProduct("5901234123457", "MFN-1", "BrandX", "Laptop Pro",
                7, 100, 200, "Elektronika > Laptopy");

        // when
        Taxonomy taxonomy = Taxonomy.from(product);

        // then
        assertNull(taxonomy.category());
        assertNull(taxonomy.categoryId());
        assertEquals("Elektronika > Laptopy", taxonomy.rawCategory());
        assertEquals(7, taxonomy.dataAccuracyScore());
        assertEquals(100, taxonomy.netWeightInGrams());
        assertEquals(200, taxonomy.grossWeightInGrams());
        assertFalse(taxonomy.isProcessable());
    }
}
