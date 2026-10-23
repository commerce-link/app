package pl.commercelink.stores;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class TrialPeriodTest {

    private static final Instant START = Instant.parse("2026-09-28T10:00:00Z");

    @Test
    void startingSetsEndTrialDaysAfterStart() {
        // when
        TrialPeriod trial = TrialPeriod.starting("owner@example.com", START, 14);

        // then
        assertEquals("owner@example.com", trial.getOwnerEmail());
        assertEquals("2026-09-28T10:00:00Z", trial.getStartedAt());
        assertEquals("2026-10-12T10:00:00Z", trial.getExpiresAt());
    }

    @Test
    void trialExpiresOnlyAfterItsEnd() {
        // given
        TrialPeriod trial = TrialPeriod.starting("owner@example.com", START, 14);

        // when / then
        assertFalse(trial.isExpired(Instant.parse("2026-10-12T09:59:59Z")));
        assertFalse(trial.isExpired(Instant.parse("2026-10-12T10:00:00Z")));
        assertTrue(trial.isExpired(Instant.parse("2026-10-12T10:00:01Z")));
    }

    @Test
    void trialWithUnreadableEndNeverExpires() {
        // given
        TrialPeriod trial = new TrialPeriod("owner@example.com", "2026-09-28T10:00:00Z", "not-a-date");

        // when / then
        assertTrue(trial.expiresAtInstant().isEmpty());
        assertFalse(trial.isExpired(Instant.parse("2030-01-01T00:00:00Z")));
    }

    @Test
    void trialWithoutEndNeverExpires() {
        // given
        TrialPeriod trial = new TrialPeriod("owner@example.com", "2026-09-28T10:00:00Z", null);

        // when / then
        assertTrue(trial.expiresAtInstant().isEmpty());
        assertFalse(trial.isExpired(Instant.parse("2030-01-01T00:00:00Z")));
    }
}
