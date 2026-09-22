package pl.commercelink.taxonomy;

import org.junit.jupiter.api.Test;
import pl.commercelink.inventory.supplier.api.SupplierProduct;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaxonomyTest {

    @Test
    void constructorDoesNotNormalizeEanOrMfn() {
        // given / when
        Taxonomy taxonomy = new Taxonomy("0590123412345", "mfn 1", "BrandX", "Laptop Pro", "Laptops", 10, null, null);

        // then
        assertEquals("0590123412345", taxonomy.ean());
        assertEquals("mfn 1", taxonomy.mfn());
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

    @Test
    void fromSupplierProductPreservesAlreadyNormalizedEanAndMfn() {
        // given
        SupplierProduct product = new SupplierProduct("0012345678905", "mfn 1", "BrandX", "Laptop Pro",
                7, 100, 200, "Elektronika > Laptopy");

        // when
        Taxonomy taxonomy = Taxonomy.from(product);

        // then
        assertEquals("012345678905", taxonomy.ean());
        assertEquals("MFN1", taxonomy.mfn());
    }

    @Test
    void hasCategoryRejectsNullAndBlank() {
        // when / then
        assertFalse(Taxonomy.hasCategory(null));
        assertFalse(Taxonomy.hasCategory(new Taxonomy("E", "M", "B", "N", null, 1, null, null)));
        assertFalse(Taxonomy.hasCategory(new Taxonomy("E", "M", "B", "N", " ", 1, null, null)));
        assertTrue(Taxonomy.hasCategory(new Taxonomy("E", "M", "B", "N", "Other", 1, null, null)));
    }

    @Test
    void bestOfPrefersCategorizedEntryOverPendingWithBetterScore() {
        // given
        Taxonomy pending = new Taxonomy("1234567890123", "MFN-PENDING", "Brand", "Name", null, 1, null, null);
        Taxonomy categorized = new Taxonomy("1234567890123", "MFN-CAT", "Brand", "Name", "CPU", 10, null, null);

        // when
        Taxonomy best = Taxonomy.bestOf(Set.of("MFN-PENDING", "MFN-CAT"),
                Map.of("MFN-PENDING", pending, "MFN-CAT", categorized));

        // then
        assertEquals("CPU", best.category());
    }

    @Test
    void bestOfReturnsPendingEntryWhenNoCategorizedCandidateExists() {
        // given
        Taxonomy pending = new Taxonomy("1234567890123", "MFN-PENDING", "Brand", "Name", null, 1, null, null);

        // when
        Taxonomy best = Taxonomy.bestOf(Set.of("MFN-PENDING"), Map.of("MFN-PENDING", pending));

        // then
        assertEquals("MFN-PENDING", best.mfn());
    }

    @Test
    void bestOfPrefersTheLowerScoreAmongEquallyCategorizedEntries() {
        // given
        Taxonomy worse = new Taxonomy("1234567890123", "MFN-A", "Brand", "Worse", "CPU", 10, null, null);
        Taxonomy better = new Taxonomy("1234567890123", "MFN-B", "Brand", "Better", "CPU", 1, null, null);

        // when
        Taxonomy best = Taxonomy.bestOf(Set.of("MFN-A", "MFN-B"), Map.of("MFN-A", worse, "MFN-B", better));

        // then
        assertEquals("Better", best.name());
    }

    @Test
    void bestOfFallsBackToEmptyWhenNoCodeIsKnown() {
        // when
        Taxonomy best = Taxonomy.bestOf(Set.of("MFN-GONE"), Map.of());

        // then
        assertThat(best).isSameAs(Taxonomy.EMPTY);
    }
}
