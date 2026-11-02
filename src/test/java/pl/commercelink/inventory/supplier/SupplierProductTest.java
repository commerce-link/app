package pl.commercelink.inventory.supplier;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SupplierProductTest {

    @Test
    void constructorUnifiesEanAndMfn() {
        // given / when
        SupplierProduct product = new SupplierProduct("0590123412345", "mfn 1", "BrandX", "Laptop Pro", 10, null, null);

        // then
        assertEquals("590123412345", product.ean());
        assertEquals("MFN1", product.mfn());
    }

    @Test
    void emptyProductHasNullRawCategory() {
        // when / then
        assertNull(SupplierProduct.EMPTY.rawCategory());
    }

    @Test
    void sevenArgConstructorLeavesRawCategoryNull() {
        // given
        SupplierProduct product = new SupplierProduct("5901234123457", "MFN-1", "BrandX", "Laptop Pro", 10, null, null);

        // when / then
        assertNull(product.rawCategory());
    }

    @Test
    void rawCategoryIsCarriedThroughCanonicalConstructor() {
        // given
        SupplierProduct product = new SupplierProduct("5901234123457", "MFN-1", "BrandX", "Laptop Pro", 10, null, null,
                "Elektronika > Laptopy");

        // when / then
        assertEquals("Elektronika > Laptopy", product.rawCategory());
    }
}
