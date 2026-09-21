package pl.commercelink.starter.security.tenant;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyApiKeyAttemptLimiterTest {

    private final LegacyApiKeyAttemptLimiter limiter = new LegacyApiKeyAttemptLimiter();

    @Test
    void doesNotBlockBelowThreshold() {
        // when
        for (int i = 0; i < 9; i++) {
            limiter.recordFailure("store-1");
        }

        // then
        assertFalse(limiter.isBlocked("store-1"));
    }

    @Test
    void blocksAfterReachingThreshold() {
        // when
        for (int i = 0; i < 10; i++) {
            limiter.recordFailure("store-1");
        }

        // then
        assertTrue(limiter.isBlocked("store-1"));
    }

    @Test
    void tracksStoresIndependently() {
        // when
        for (int i = 0; i < 10; i++) {
            limiter.recordFailure("store-1");
        }

        // then
        assertTrue(limiter.isBlocked("store-1"));
        assertFalse(limiter.isBlocked("store-2"));
    }

    @Test
    void resetClearsFailures() {
        // given
        for (int i = 0; i < 10; i++) {
            limiter.recordFailure("store-1");
        }

        // when
        limiter.reset("store-1");

        // then
        assertFalse(limiter.isBlocked("store-1"));
    }
}
