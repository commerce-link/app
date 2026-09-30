package pl.commercelink.stores;

import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.starter.dynamodb.OptimisticLockingExecutor;
import pl.commercelink.testsupport.OptimisticLockingExecutorMocks;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StoreTrialServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-28T10:00:00Z");
    private static final String STORE_ID = "abc123def4";

    @Mock private StoresRepository storesRepository;
    @Mock private OptimisticLockingExecutor optimisticLockingExecutor;

    private StoreTrialService service() {
        return new StoreTrialService(storesRepository, optimisticLockingExecutor, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static Store trialStore(String expiresAt) {
        Store store = new Store();
        store.setStoreId(STORE_ID);
        store.setTrial(new TrialPeriod("owner@example.com", "2026-09-14T10:00:00Z", expiresAt));
        return store;
    }

    private static Store fullStore() {
        Store store = new Store();
        store.setStoreId(STORE_ID);
        return store;
    }

    private void lockingRetriesOnConflict() {
        doAnswer(OptimisticLockingExecutorMocks.retryingModifyAndSave(3))
                .when(optimisticLockingExecutor).modifyAndSave(any(), any(), any());
    }

    @Test
    void storeWithoutTrialHasNoStatus() {
        // when / then
        assertTrue(service().status(fullStore()).isEmpty());
        assertTrue(service().status(null).isEmpty());
    }

    @Test
    void statusCountsCalendarDaysUntilTheEndDate() {
        // given
        Store store = trialStore("2026-10-11T11:00:00Z");

        // when
        TrialStatus status = service().status(store).orElseThrow();

        // then
        assertEquals(13, status.daysLeft());
        assertEquals(LocalDate.parse("2026-10-11"), status.endsOn());
        assertFalse(status.expired());
    }

    @Test
    void trialEndingLaterTodayHasNoDaysLeftButIsNotExpired() {
        // given
        Store store = trialStore("2026-09-28T20:00:00Z");

        // when
        TrialStatus status = service().status(store).orElseThrow();

        // then
        assertEquals(0, status.daysLeft());
        assertEquals(LocalDate.parse("2026-09-28"), status.endsOn());
        assertFalse(status.expired());
    }

    @Test
    void statusOfEndedTrialIsExpiredWithNoDaysLeft() {
        // given
        Store store = trialStore("2026-09-27T10:00:00Z");

        // when
        TrialStatus status = service().status(store).orElseThrow();

        // then
        assertTrue(status.expired());
        assertEquals(0, status.daysLeft());
    }

    @Test
    void statusShowsTheEndDateInPolishTime() {
        // given
        Store store = trialStore("2026-10-11T23:30:00Z");

        // when
        TrialStatus status = service().status(store).orElseThrow();

        // then
        assertEquals(LocalDate.parse("2026-10-12"), status.endsOn());
    }

    @Test
    void trialWithUnreadableEndHasNoStatus() {
        // when / then
        assertTrue(service().status(trialStore("not-a-date")).isEmpty());
    }

    @Test
    void convertRemovesTrialAndSavesStore() {
        // given
        Store store = trialStore("2026-10-12T10:00:00Z");
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        lockingRetriesOnConflict();

        // when
        boolean converted = service().convertToFullAccount(STORE_ID);

        // then
        assertTrue(converted);
        assertNull(store.getTrial());
        verify(storesRepository).save(store);
    }

    @Test
    void convertBringsBackStoreWhoseTrialEnded() {
        // given
        Store store = trialStore("2026-09-20T10:00:00Z");
        store.setActive(false);
        store.setDeactivation(new StoreDeactivation(DeactivationReason.TRIAL_ENDED, "2026-09-20T10:00:00Z",
                "2026-09-20T11:00:00Z", "2026-09-20T11:00:00Z"));
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        lockingRetriesOnConflict();

        // when
        boolean converted = service().convertToFullAccount(STORE_ID);

        // then
        assertTrue(converted);
        assertTrue(store.isActive(NOW));
        assertNull(store.getDeactivation());
        verify(storesRepository).save(store);
    }

    @Test
    void convertLeavesFullAccountUntouched() {
        // given
        when(storesRepository.findById(STORE_ID)).thenReturn(fullStore());
        lockingRetriesOnConflict();

        // when
        boolean converted = service().convertToFullAccount(STORE_ID);

        // then
        assertFalse(converted);
        verify(storesRepository, never()).save(any());
    }

    @Test
    void convertOfMissingStoreChangesNothing() {
        // given
        when(storesRepository.findById(STORE_ID)).thenReturn(null);
        lockingRetriesOnConflict();

        // when
        boolean converted = service().convertToFullAccount(STORE_ID);

        // then
        assertFalse(converted);
        verify(storesRepository, never()).save(any());
    }

    @Test
    void convertRetriesOnVersionConflict() {
        // given
        Store stale = trialStore("2026-10-12T10:00:00Z");
        Store fresh = trialStore("2026-10-12T10:00:00Z");
        when(storesRepository.findById(STORE_ID)).thenReturn(stale, fresh);
        doThrow(new ConditionalCheckFailedException("version changed")).when(storesRepository).save(stale);
        lockingRetriesOnConflict();

        // when
        boolean converted = service().convertToFullAccount(STORE_ID);

        // then
        assertTrue(converted);
        assertNull(fresh.getTrial());
        verify(storesRepository).save(fresh);
    }

    @Test
    void convertReportsNothingWhenTheStoreWasConvertedMeanwhile() {
        // given
        Store stale = trialStore("2026-10-12T10:00:00Z");
        Store converted = fullStore();
        when(storesRepository.findById(STORE_ID)).thenReturn(stale, converted);
        doThrow(new ConditionalCheckFailedException("version changed")).when(storesRepository).save(stale);
        lockingRetriesOnConflict();

        // when
        boolean result = service().convertToFullAccount(STORE_ID);

        // then
        assertFalse(result);
        verify(storesRepository, never()).save(converted);
    }
}
