package pl.commercelink.inventory.supplier;

import org.junit.jupiter.api.Test;
import pl.commercelink.inventory.supplier.api.Taxonomy;
import pl.commercelink.taxonomy.ProductCategory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class StoreFeedTaxonomyTest {

    private Taxonomy taxonomy(int score) {
        return new Taxonomy("5900000000001", "MFN1", "Brand", "Name", ProductCategory.CPU, score, 100, 120);
    }

    @Test
    void addsPenaltyToScoreAndPreservesOtherFields() {
        Taxonomy result = StoreFeedTaxonomy.deprioritized(taxonomy(5), 1000);

        assertEquals(1005, result.dataAccuracyScore());
        assertEquals("5900000000001", result.ean());
        assertEquals("MFN1", result.mfn());
        assertEquals("Brand", result.brand());
        assertEquals("Name", result.name());
        assertEquals(ProductCategory.CPU, result.category());
        assertEquals(100, result.netWeightInGrams());
        assertEquals(120, result.grossWeightInGrams());
    }

    @Test
    void returnsSameInstanceWhenPenaltyZeroOrNegative() {
        Taxonomy base = taxonomy(5);

        assertSame(base, StoreFeedTaxonomy.deprioritized(base, 0));
        assertSame(base, StoreFeedTaxonomy.deprioritized(base, -3));
    }
}
