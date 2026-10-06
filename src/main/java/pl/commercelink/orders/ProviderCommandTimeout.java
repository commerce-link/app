package pl.commercelink.orders;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * How long a shipment creation or pickup command may stay PENDING. Longer than its whole check chain plus a redelivery
 * from the dead-letter queue: a command still PENDING after it was never going to be settled (the JVM stopped between
 * the mark and the publish, or the check kept failing), so it counts as unconfirmed and the operator may act on it.
 */
public final class ProviderCommandTimeout {

    public static final Duration UNCONFIRMED_AFTER = Duration.ofMinutes(10);

    private ProviderCommandTimeout() {
    }

    static boolean isOverdue(LocalDateTime requestedAt, LocalDateTime now) {
        return requestedAt == null || requestedAt.plus(UNCONFIRMED_AFTER).isBefore(now);
    }
}
