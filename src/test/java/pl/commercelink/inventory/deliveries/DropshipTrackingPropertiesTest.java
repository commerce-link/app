package pl.commercelink.inventory.deliveries;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DropshipTrackingPropertiesTest {

    @Test
    void defaultsMatchSpec() {
        // when
        DropshipTrackingProperties properties = DropshipTrackingProperties.defaults();

        // then
        assertThat(properties.initialDelay()).isEqualTo(Duration.ofMinutes(30));
        assertThat(properties.intervalFirstDay()).isEqualTo(Duration.ofMinutes(30));
        assertThat(properties.intervalLater()).isEqualTo(Duration.ofHours(2));
        assertThat(properties.maxAge()).isEqualTo(Duration.ofDays(14));
        assertThat(properties.maxConsecutiveErrors()).isEqualTo(5);
    }

    @Test
    void intervalDependsOnAge() {
        // given
        DropshipTrackingProperties properties = DropshipTrackingProperties.defaults();

        // when / then
        assertThat(properties.intervalFor(Duration.ofHours(23))).isEqualTo(Duration.ofMinutes(30));
        assertThat(properties.intervalFor(Duration.ofHours(25))).isEqualTo(Duration.ofHours(2));
    }

    @Test
    void rejectsNonPositiveIntervalsAndZeroErrors() {
        // when / then
        assertThrows(IllegalArgumentException.class, () -> new DropshipTrackingProperties(
                Duration.ofMinutes(-1), Duration.ofMinutes(30), Duration.ofHours(2), Duration.ofDays(14), 5));
        assertThrows(IllegalArgumentException.class, () -> new DropshipTrackingProperties(
                Duration.ZERO, Duration.ZERO, Duration.ofHours(2), Duration.ofDays(14), 5));
        assertThrows(IllegalArgumentException.class, () -> new DropshipTrackingProperties(
                Duration.ZERO, Duration.ofMinutes(30), Duration.ofHours(2), Duration.ofDays(14), 0));
    }

    @Test
    void zeroInitialDelayIsAllowedForLocalTesting() {
        // when
        DropshipTrackingProperties properties = new DropshipTrackingProperties(
                Duration.ZERO, Duration.ofMinutes(1), Duration.ofMinutes(1), Duration.ofDays(1), 1);

        // then
        assertThat(properties.initialDelay()).isZero();
    }
}
