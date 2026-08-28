package pl.commercelink.inventory.deliveries;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties(prefix = "dropship.tracking")
public record DropshipTrackingProperties(
        @DefaultValue("PT30M") Duration initialDelay,
        @DefaultValue("PT30M") Duration intervalFirstDay,
        @DefaultValue("PT2H") Duration intervalLater,
        @DefaultValue("P14D") Duration maxAge,
        @DefaultValue("5") int maxConsecutiveErrors) {

    public DropshipTrackingProperties {
        if (initialDelay == null || initialDelay.isNegative()) {
            throw new IllegalArgumentException("dropship.tracking.initial-delay must not be negative, got: " + initialDelay);
        }
        requirePositive(intervalFirstDay, "interval-first-day");
        requirePositive(intervalLater, "interval-later");
        requirePositive(maxAge, "max-age");
        if (maxConsecutiveErrors < 1) {
            throw new IllegalArgumentException("dropship.tracking.max-consecutive-errors must be at least 1, got: " + maxConsecutiveErrors);
        }
    }

    public static DropshipTrackingProperties defaults() {
        return new DropshipTrackingProperties(Duration.ofMinutes(30), Duration.ofMinutes(30),
                Duration.ofHours(2), Duration.ofDays(14), 5);
    }

    public Duration intervalFor(Duration age) {
        return age.compareTo(Duration.ofDays(1)) < 0 ? intervalFirstDay : intervalLater;
    }

    private static void requirePositive(Duration value, String key) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException("dropship.tracking." + key + " must be positive, got: " + value);
        }
    }
}
