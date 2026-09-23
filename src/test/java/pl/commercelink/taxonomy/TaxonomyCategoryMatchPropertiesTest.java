package pl.commercelink.taxonomy;

import org.junit.jupiter.api.Test;
import pl.commercelink.taxonomy.TaxonomyCategoryMatchProperties.Mapping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TaxonomyCategoryMatchPropertiesTest {

    @Test
    void rejectsMaxSubmissionsPerRunBelowOne() {
        // when / then
        assertThrows(IllegalArgumentException.class, () -> new TaxonomyCategoryMatchProperties(1000, 0));
        assertThrows(IllegalArgumentException.class, () -> new TaxonomyCategoryMatchProperties(1000, -5));
    }

    @Test
    void acceptsASingleSubmissionPerRun() {
        // when
        TaxonomyCategoryMatchProperties properties = new TaxonomyCategoryMatchProperties(1000, 1);

        // then
        assertEquals(1, properties.maxSubmissionsPerRun());
    }

    @Test
    void mappingDefaultsAreAppliedByShortConstructor() {
        // when
        TaxonomyCategoryMatchProperties properties = new TaxonomyCategoryMatchProperties(1000, 10);

        // then
        assertEquals(5, properties.mapping().minSamples());
        assertEquals(0.9, properties.mapping().minShare());
        assertEquals(0.9, properties.mapping().minConfidence());
        assertEquals(20, properties.mapping().trickleEvery());
        assertEquals(4, properties.maxAttempts());
    }

    @Test
    void rejectsInvalidMappingThresholds() {
        // when / then
        assertThrows(IllegalArgumentException.class, () -> new Mapping(0, 0.9, 0.9, 20));
        assertThrows(IllegalArgumentException.class, () -> new Mapping(5, 1.5, 0.9, 20));
        assertThrows(IllegalArgumentException.class, () -> new Mapping(5, 0.9, -0.1, 20));
        assertThrows(IllegalArgumentException.class, () -> new Mapping(5, 0.9, 0.9, 0));
    }

    @Test
    void rejectsNegativeMaxAttempts() {
        // when / then
        assertThrows(IllegalArgumentException.class,
                () -> new TaxonomyCategoryMatchProperties(1000, 10, new Mapping(5, 0.9, 0.9, 20), -1));
    }

    @Test
    void acceptsZeroMaxAttemptsAsDisabled() {
        // when
        TaxonomyCategoryMatchProperties properties =
                new TaxonomyCategoryMatchProperties(1000, 10, new Mapping(5, 0.9, 0.9, 20), 0);

        // then
        assertEquals(0, properties.maxAttempts());
    }
}
