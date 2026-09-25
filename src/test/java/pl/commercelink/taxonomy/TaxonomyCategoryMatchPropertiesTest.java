package pl.commercelink.taxonomy;

import org.junit.jupiter.api.Test;
import pl.commercelink.taxonomy.TaxonomyCategoryMatchProperties.Mapping;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TaxonomyCategoryMatchPropertiesTest {

    private static final Mapping MAPPING = new Mapping(5, 0.9, 0.9, 20);
    private static final Duration RETRY_EXHAUSTED_AFTER = Duration.ofDays(7);
    private static final Duration RETRY_AFTER = Duration.ofHours(1);

    @Test
    void rejectsMaxSubmissionsPerRunBelowOne() {
        // when / then
        assertThrows(IllegalArgumentException.class,
                () -> new TaxonomyCategoryMatchProperties(1000, 0, MAPPING, 4, RETRY_EXHAUSTED_AFTER, RETRY_AFTER));
        assertThrows(IllegalArgumentException.class,
                () -> new TaxonomyCategoryMatchProperties(1000, -5, MAPPING, 4, RETRY_EXHAUSTED_AFTER, RETRY_AFTER));
    }

    @Test
    void acceptsASingleSubmissionPerRun() {
        // when
        TaxonomyCategoryMatchProperties properties =
                new TaxonomyCategoryMatchProperties(1000, 1, MAPPING, 4, RETRY_EXHAUSTED_AFTER, RETRY_AFTER);

        // then
        assertEquals(1, properties.maxSubmissionsPerRun());
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
                () -> new TaxonomyCategoryMatchProperties(1000, 10, MAPPING, -1, RETRY_EXHAUSTED_AFTER, RETRY_AFTER));
    }

    @Test
    void acceptsZeroMaxAttemptsAsDisabled() {
        // when
        TaxonomyCategoryMatchProperties properties =
                new TaxonomyCategoryMatchProperties(1000, 10, MAPPING, 0, RETRY_EXHAUSTED_AFTER, RETRY_AFTER);

        // then
        assertEquals(0, properties.maxAttempts());
    }

    @Test
    void rejectsANonPositiveRetryWindow() {
        // when / then
        assertThrows(IllegalArgumentException.class,
                () -> new TaxonomyCategoryMatchProperties(1000, 10, MAPPING, 4, Duration.ZERO, RETRY_AFTER));
        assertThrows(IllegalArgumentException.class,
                () -> new TaxonomyCategoryMatchProperties(1000, 10, MAPPING, 4, Duration.ofDays(-1), RETRY_AFTER));
    }
}
