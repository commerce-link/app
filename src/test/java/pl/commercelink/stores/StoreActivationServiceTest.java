package pl.commercelink.stores;

import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.starter.dynamodb.OptimisticLockingExecutor;
import pl.commercelink.testsupport.OptimisticLockingExecutorMocks;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StoreActivationServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-20T10:00:00Z");
    private static final String STORE_ID = "abc123def4";

    @Mock private StoresRepository storesRepository;
    @Mock private OptimisticLockingExecutor optimisticLockingExecutor;

    private StoreActivationService service;

    @BeforeEach
    void setUp() {
        service = new StoreActivationService(storesRepository, optimisticLockingExecutor, Clock.fixed(NOW, ZoneOffset.UTC));
        doAnswer(OptimisticLockingExecutorMocks.retryingModifyAndSave(3))
                .when(optimisticLockingExecutor).modifyAndSave(any(), any(), any());
    }

    private static Store store() {
        Store store = new Store();
        store.setStoreId(STORE_ID);
        return store;
    }

    private static Store switchedOff() {
        Store store = store();
        store.setActive(false);
        store.setDeactivation(StoreDeactivation.of(DeactivationReason.MANUAL, Instant.parse("2026-10-18T10:00:00Z")));
        return store;
    }

    @Test
    void deactivationSwitchesTheStoreOffAndRecordsWhenAndWhy() {
        // given
        Store store = store();
        when(storesRepository.findById(STORE_ID)).thenReturn(store);

        // when
        StoreActivationService.Outcome outcome = service.deactivate(STORE_ID);

        // then
        assertEquals(StoreActivationService.Outcome.CHANGED, outcome);
        assertEquals(Boolean.FALSE, store.getActive());
        assertEquals(DeactivationReason.MANUAL, store.getDeactivation().getReason());
        assertEquals(NOW.toString(), store.getDeactivation().getDeactivatedAt());
        assertNull(store.getDeactivation().getOwnerNotifiedAt());
        verify(storesRepository).save(store);
    }

    @Test
    void deactivationLeavesInactiveStoreAsItIs() {
        // given
        when(storesRepository.findById(STORE_ID)).thenReturn(switchedOff());

        // when / then
        assertEquals(StoreActivationService.Outcome.UNCHANGED, service.deactivate(STORE_ID));
        verify(storesRepository, never()).save(any());
    }

    @Test
    void deactivationLeavesStoreWhoseTrialEndedAsItIs() {
        // given
        Store store = store();
        store.setTrial(new TrialPeriod("owner@example.com", "2026-09-28T10:00:00Z", "2026-10-12T10:00:00Z"));
        when(storesRepository.findById(STORE_ID)).thenReturn(store);

        // when / then
        assertEquals(StoreActivationService.Outcome.UNCHANGED, service.deactivate(STORE_ID));
        verify(storesRepository, never()).save(any());
    }

    @Test
    void deactivationReportsMissingStore() {
        // given
        when(storesRepository.findById(STORE_ID)).thenReturn(null);

        // when / then
        assertEquals(StoreActivationService.Outcome.MISSING, service.deactivate(STORE_ID));
        verify(storesRepository, never()).save(any());
    }

    @Test
    void activationSwitchesTheStoreBackOn() {
        // given
        Store store = switchedOff();
        when(storesRepository.findById(STORE_ID)).thenReturn(store);

        // when
        StoreActivationService.Outcome outcome = service.activate(STORE_ID);

        // then
        assertEquals(StoreActivationService.Outcome.CHANGED, outcome);
        assertEquals(Boolean.TRUE, store.getActive());
        assertNull(store.getDeactivation());
        verify(storesRepository).save(store);
    }

    @Test
    void activationBringsBackRunningTrialSwitchedOffByHand() {
        // given
        Store store = switchedOff();
        store.setTrial(new TrialPeriod("owner@example.com", "2026-10-15T10:00:00Z", "2026-10-29T10:00:00Z"));
        when(storesRepository.findById(STORE_ID)).thenReturn(store);

        // when / then
        assertEquals(StoreActivationService.Outcome.CHANGED, service.activate(STORE_ID));
        assertTrue(store.isActive(NOW));
    }

    @Test
    void activationRefusesStoreWhoseTrialEnded() {
        // given
        Store store = store();
        store.setTrial(new TrialPeriod("owner@example.com", "2026-09-28T10:00:00Z", "2026-10-12T10:00:00Z"));
        store.setActive(false);
        when(storesRepository.findById(STORE_ID)).thenReturn(store);

        // when / then
        assertEquals(StoreActivationService.Outcome.TRIAL_ENDED, service.activate(STORE_ID));
        verify(storesRepository, never()).save(any());
    }

    @Test
    void activationLeavesActiveStoreAsItIs() {
        // given
        when(storesRepository.findById(STORE_ID)).thenReturn(store());

        // when / then
        assertEquals(StoreActivationService.Outcome.UNCHANGED, service.activate(STORE_ID));
        verify(storesRepository, never()).save(any());
    }

    @Test
    void activationRetriesOnVersionConflict() {
        // given
        Store stale = switchedOff();
        Store fresh = switchedOff();
        when(storesRepository.findById(STORE_ID)).thenReturn(stale, fresh);
        doThrow(new ConditionalCheckFailedException("version changed")).when(storesRepository).save(stale);

        // when
        StoreActivationService.Outcome outcome = service.activate(STORE_ID);

        // then
        assertEquals(StoreActivationService.Outcome.CHANGED, outcome);
        verify(storesRepository).save(fresh);
        assertNull(fresh.getDeactivation());
    }
}
