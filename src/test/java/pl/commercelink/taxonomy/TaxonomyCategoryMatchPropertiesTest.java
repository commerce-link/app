package pl.commercelink.taxonomy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaxonomyCategoryMatchPropertiesTest {

    @Test
    void rejectsBucketsBelowOne() {
        // when / then
        assertThrows(IllegalArgumentException.class, () -> new TaxonomyCategoryMatchProperties(true, 0, 100));
        assertThrows(IllegalArgumentException.class, () -> new TaxonomyCategoryMatchProperties(true, -5, 100));
    }

    @Test
    void acceptsSingleBucket() {
        // when
        TaxonomyCategoryMatchProperties properties = new TaxonomyCategoryMatchProperties(true, 1, 100);

        // then
        assertEquals(1, properties.buckets());
    }

    @Test
    void enabledReflectsConfiguredFlag() {
        // given
        TaxonomyCategoryMatchProperties on = new TaxonomyCategoryMatchProperties(true, 100, 300000);
        TaxonomyCategoryMatchProperties off = new TaxonomyCategoryMatchProperties(false, 100, 300000);

        // when / then
        assertTrue(on.enabled());
        assertFalse(off.enabled());
    }
}
