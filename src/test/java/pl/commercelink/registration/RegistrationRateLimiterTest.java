package pl.commercelink.registration;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class RegistrationRateLimiterTest {

    private static final Instant START = Instant.parse("2026-07-08T10:00:00Z");

    private final AtomicReference<Instant> now = new AtomicReference<>(START);
    private final Clock clock = new Clock() {
        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now.get(); }
    };

    @Test
    void limitsRegistrationsPerIpPerHour() {
        // given
        RegistrationRateLimiter limiter = new RegistrationRateLimiter(clock, 2, 100);

        // when / then
        assertTrue(limiter.tryAcquire("1.1.1.1"));
        assertTrue(limiter.tryAcquire("1.1.1.1"));
        assertFalse(limiter.tryAcquire("1.1.1.1"));
        assertTrue(limiter.tryAcquire("2.2.2.2"));
    }

    @Test
    void ipWindowSlidesAfterAnHour() {
        // given
        RegistrationRateLimiter limiter = new RegistrationRateLimiter(clock, 1, 100);
        assertTrue(limiter.tryAcquire("1.1.1.1"));
        assertFalse(limiter.tryAcquire("1.1.1.1"));

        // when
        now.set(START.plusSeconds(3601));

        // then
        assertTrue(limiter.tryAcquire("1.1.1.1"));
        assertFalse(limiter.tryAcquire("1.1.1.1"));
    }

    @Test
    void enforcesGlobalDailyLimit() {
        // given
        RegistrationRateLimiter limiter = new RegistrationRateLimiter(clock, 10, 2);
        assertTrue(limiter.tryAcquire("1.1.1.1"));
        assertTrue(limiter.tryAcquire("2.2.2.2"));

        // when / then
        assertFalse(limiter.tryAcquire("3.3.3.3"));

        // when
        now.set(START.plusSeconds(86401));

        // then
        assertTrue(limiter.tryAcquire("3.3.3.3"));
    }
}
