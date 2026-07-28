package pl.commercelink.taxonomy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TaxonomyCategoryMatchPropertiesTest {

    @Test
    void rejectsBucketsBelowOne() {
        // when / then
        assertThrows(IllegalArgumentException.class, () -> new TaxonomyCategoryMatchProperties(0, 100));
        assertThrows(IllegalArgumentException.class, () -> new TaxonomyCategoryMatchProperties(-5, 100));
    }

    @Test
    void acceptsSingleBucket() {
        // when
        TaxonomyCategoryMatchProperties properties = new TaxonomyCategoryMatchProperties(1, 100);

        // then
        assertEquals(1, properties.buckets());
    }

    @Test
    void mappingDefaultsAreAppliedByShortConstructor() {
        // when
        TaxonomyCategoryMatchProperties properties = new TaxonomyCategoryMatchProperties(100, 300000);

        // then
        assertEquals(5, properties.mappingMinSamples());
        assertEquals(0.9, properties.mappingMinShare());
        assertEquals(0.9, properties.mappingMinConfidence());
        assertEquals(20, properties.mappingTrickleEvery());
    }

    @Test
    void rejectsInvalidMappingThresholds() {
        // when / then
        assertThrows(IllegalArgumentException.class, () -> new TaxonomyCategoryMatchProperties(1, 100, 0, 0.9, 0.9, 20));
        assertThrows(IllegalArgumentException.class, () -> new TaxonomyCategoryMatchProperties(1, 100, 5, 1.5, 0.9, 20));
        assertThrows(IllegalArgumentException.class, () -> new TaxonomyCategoryMatchProperties(1, 100, 5, 0.9, -0.1, 20));
        assertThrows(IllegalArgumentException.class, () -> new TaxonomyCategoryMatchProperties(1, 100, 5, 0.9, 0.9, 0));
    }
}
