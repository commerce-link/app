package pl.commercelink.stores;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StoreActivityTest {

    private static final Instant NOW = Instant.parse("2026-10-20T10:00:00Z");
    private static final String STORE_ID = "abc123def4";

    @Mock private StoresRepository storesRepository;

    private StoreActivity activity() {
        return new StoreActivity(storesRepository, 14, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static Store store() {
        Store store = new Store();
        store.setStoreId(STORE_ID);
        return store;
    }

    private static Store trialStore(String expiresAt) {
        Store store = store();
        store.setTrial(new TrialPeriod("owner@example.com", "2026-09-28T10:00:00Z", expiresAt));
        return store;
    }

    @Test
    void storeWithoutTheFlagIsActive() {
        // when / then
        assertTrue(activity().isActive(store()));
    }

    @Test
    void storeSwitchedOffIsInactiveAndSwitchedOnIsActive() {
        // given
        Store off = store();
        off.setActive(false);
        Store on = store();
        on.setActive(true);

        // when / then
        assertFalse(activity().isActive(off));
        assertTrue(activity().isActive(on));
    }

    @Test
    void trialStoreIsActiveUntilItsTrialEnds() {
        // when / then
        assertTrue(activity().isActive(trialStore("2026-10-21T10:00:00Z")));
        assertFalse(activity().isActive(trialStore("2026-10-20T09:59:59Z")));
    }

    @Test
    void endedTrialKeepsTheStoreInactiveEvenWithTheFlagOn() {
        // given
        Store store = trialStore("2026-10-12T10:00:00Z");
        store.setActive(true);

        // when / then
        assertFalse(activity().isActive(store));
    }

    @Test
    void trialWithUnreadableEndDoesNotHoldTheStoreBack() {
        // when / then
        assertTrue(activity().isActive(trialStore("not-a-date")));
    }

    @Test
    void storeThatDoesNotExistDoesNoWork() {
        // given
        when(storesRepository.findById(STORE_ID)).thenReturn(null);

        // when / then
        assertFalse(activity().isActive(STORE_ID));
        assertFalse(activity().isActive((Store) null));
    }

    @Test
    void storeIsReadByItsId() {
        // given
        when(storesRepository.findById(STORE_ID)).thenReturn(store());

        // when / then
        assertTrue(activity().isActive(STORE_ID));
    }

    @Test
    void missingIdIsNeverLookedUp() {
        // when / then
        assertFalse(activity().isActive((String) null));
        verifyNoInteractions(storesRepository);
    }

    @Test
    void trialStoreIsDeletedRetentionDaysAfterItsTrialEnds() {
        // when
        Optional<Instant> due = activity().deletionDueAt(trialStore("2026-10-12T10:00:00Z"));

        // then
        assertEquals(Optional.of(Instant.parse("2026-10-26T10:00:00Z")), due);
    }

    @Test
    void storeWithoutTrialIsNeverDeletedThisWay() {
        // when / then
        assertTrue(activity().deletionDueAt(store()).isEmpty());
        assertTrue(activity().deletionDueAt(trialStore("not-a-date")).isEmpty());
    }

    @Test
    void activeStoreHasNoDeactivationStatus() {
        // when / then
        assertTrue(activity().status(store()).isEmpty());
        assertTrue(activity().status(null).isEmpty());
    }

    @Test
    void endedTrialCountsTheDaysUntilDeletion() {
        // when
        DeactivationStatus status = activity().status(trialStore("2026-10-12T10:00:00Z")).orElseThrow();

        // then
        assertEquals(DeactivationReason.TRIAL_ENDED, status.reason());
        assertEquals(LocalDate.parse("2026-10-12"), status.deactivatedOn());
        assertEquals(LocalDate.parse("2026-10-26"), status.deletionOn());
        assertEquals(6, status.daysUntilDeletion());
    }

    @Test
    void daysUntilDeletionNeverGoBelowZero() {
        // when
        DeactivationStatus status = activity().status(trialStore("2026-09-01T10:00:00Z")).orElseThrow();

        // then
        assertEquals(0, status.daysUntilDeletion());
    }

    @Test
    void datesOfEndedTrialAreInPolishTime() {
        // when
        DeactivationStatus status = activity().status(trialStore("2026-10-11T23:30:00Z")).orElseThrow();

        // then
        assertEquals(LocalDate.parse("2026-10-12"), status.deactivatedOn());
        assertEquals(LocalDate.parse("2026-10-26"), status.deletionOn());
    }

    @Test
    void storeSwitchedOffByHandIsNeverScheduledForDeletion() {
        // given
        Store store = store();
        store.setActive(false);
        store.setDeactivation(StoreDeactivation.of(DeactivationReason.MANUAL, Instant.parse("2026-10-15T22:30:00Z")));

        // when
        DeactivationStatus status = activity().status(store).orElseThrow();

        // then
        assertEquals(DeactivationReason.MANUAL, status.reason());
        assertEquals(LocalDate.parse("2026-10-16"), status.deactivatedOn());
        assertNull(status.deletionOn());
    }

    @Test
    void storeSwitchedOffWithoutRecordHasNoDeactivationDay() {
        // given
        Store store = store();
        store.setActive(false);

        // when
        DeactivationStatus status = activity().status(store).orElseThrow();

        // then
        assertEquals(DeactivationReason.MANUAL, status.reason());
        assertNull(status.deactivatedOn());
    }

    @Test
    void runningTrialSwitchedOffByHandCountsAsManualUntilTheTrialEnds() {
        // given
        Store running = trialStore("2026-10-25T10:00:00Z");
        running.setActive(false);
        Store ended = trialStore("2026-10-19T10:00:00Z");
        ended.setActive(false);

        // when / then
        assertEquals(DeactivationReason.MANUAL, activity().status(running).orElseThrow().reason());
        assertEquals(DeactivationReason.TRIAL_ENDED, activity().status(ended).orElseThrow().reason());
    }
}
