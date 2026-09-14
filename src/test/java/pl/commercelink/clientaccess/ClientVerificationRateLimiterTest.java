package pl.commercelink.clientaccess;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientVerificationRateLimiterTest {

    private static final Instant START = Instant.parse("2026-09-12T10:00:00Z");

    private final AtomicReference<Instant> now = new AtomicReference<>(START);
    private final Clock clock = new Clock() {
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now.get(); }
    };

    @Test
    void limitsRequestsPerIpPerHour() {
        // given
        ClientVerificationRateLimiter limiter = new ClientVerificationRateLimiter(clock, 2);

        // when / then
        assertTrue(limiter.tryAcquire("1.1.1.1"));
        assertTrue(limiter.tryAcquire("1.1.1.1"));
        assertFalse(limiter.tryAcquire("1.1.1.1"));
        assertTrue(limiter.tryAcquire("2.2.2.2"));
    }

    @Test
    void ipWindowSlidesAfterAnHour() {
        // given
        ClientVerificationRateLimiter limiter = new ClientVerificationRateLimiter(clock, 1);
        assertTrue(limiter.tryAcquire("1.1.1.1"));
        assertFalse(limiter.tryAcquire("1.1.1.1"));

        // when
        now.set(START.plusSeconds(3601));

        // then
        assertTrue(limiter.tryAcquire("1.1.1.1"));
        assertFalse(limiter.tryAcquire("1.1.1.1"));
    }
}
