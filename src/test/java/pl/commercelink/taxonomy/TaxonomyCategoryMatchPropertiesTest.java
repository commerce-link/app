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
        assertThrows(IllegalArgumentException.class, () -> new TaxonomyCategoryMatchProperties("Acme", 0, 100));
        assertThrows(IllegalArgumentException.class, () -> new TaxonomyCategoryMatchProperties("Acme", -5, 100));
    }

    @Test
    void acceptsSingleBucket() {
        // when
        TaxonomyCategoryMatchProperties properties = new TaxonomyCategoryMatchProperties("Acme", 1, 100);

        // then
        assertEquals(1, properties.buckets());
    }

    @Test
    void allowsExactlyConfiguredSupplier() {
        // given
        TaxonomyCategoryMatchProperties props = new TaxonomyCategoryMatchProperties("Acme, Elko", 100, 300000);

        // when / then
        assertTrue(props.allows("Acme"));
        assertFalse(props.allows("Morele"));
    }

    @Test
    void allowsAnyManualIdentityWhenManualConfigured() {
        // given
        TaxonomyCategoryMatchProperties props = new TaxonomyCategoryMatchProperties("Manual", 100, 300000);

        // when / then
        assertTrue(props.allows("manual:Hurtownia A"));
        assertTrue(props.allows("manual:Inny Sklep"));
    }

    @Test
    void rejectsManualIdentityWhenManualNotConfigured() {
        // given
        TaxonomyCategoryMatchProperties props = new TaxonomyCategoryMatchProperties("Acme", 100, 300000);

        // when / then
        assertFalse(props.allows("manual:Hurtownia A"));
    }

    @Test
    void rejectsNullSupplier() {
        // given
        TaxonomyCategoryMatchProperties props = new TaxonomyCategoryMatchProperties("Manual", 100, 300000);

        // when / then
        assertFalse(props.allows(null));
    }
}
