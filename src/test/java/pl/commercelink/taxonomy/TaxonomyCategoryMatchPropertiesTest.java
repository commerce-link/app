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
}
