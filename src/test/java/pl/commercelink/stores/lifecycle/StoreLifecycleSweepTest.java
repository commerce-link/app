package pl.commercelink.stores.lifecycle;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.marketplace.MarketplaceOfferWithdrawal;
import pl.commercelink.starter.dynamodb.OptimisticLockingExecutor;
import pl.commercelink.stores.DeactivationReason;
import pl.commercelink.stores.DeactivationStatus;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoreActivity;
import pl.commercelink.stores.StoreDeactivation;
import pl.commercelink.stores.StoreDeletionService;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.stores.TrialPeriod;
import pl.commercelink.testsupport.OptimisticLockingExecutorMocks;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StoreLifecycleSweepTest {

    private static final Instant NOW = Instant.parse("2026-10-20T10:00:00Z");
    private static final String STORE_ID = "abc123def4";
    private static final String TRIAL_END = "2026-10-12T10:00:00Z";
    private static final DeactivationStatus TRIAL_ENDED = new DeactivationStatus(DeactivationReason.TRIAL_ENDED,
            LocalDate.parse("2026-10-12"), LocalDate.parse("2026-10-26"), 6);

    @Mock private StoresRepository storesRepository;
    @Mock private StoreActivity storeActivity;
    @Mock private OptimisticLockingExecutor optimisticLockingExecutor;
    @Mock private MarketplaceOfferWithdrawal offerWithdrawal;
    @Mock private TrialEndedNotice trialEndedNotice;
    @Mock private StoreDeletionService storeDeletionService;

    private StoreLifecycleSweep sweep;

    @BeforeEach
    void setUp() {
        sweep = new StoreLifecycleSweep(storesRepository, storeActivity, optimisticLockingExecutor, offerWithdrawal,
                trialEndedNotice, storeDeletionService, Clock.fixed(NOW, ZoneOffset.UTC));
        lenient().doAnswer(OptimisticLockingExecutorMocks.retryingModifyAndSave(3))
                .when(optimisticLockingExecutor).modifyAndSave(any(), any(), any());
    }

    private static Store trialStore(String expiresAt) {
        Store store = new Store();
        store.setStoreId(STORE_ID);
        store.setTrial(new TrialPeriod("owner@example.com", "2026-09-28T10:00:00Z", expiresAt));
        return store;
    }

    private static Store endedTrial(String offersWithdrawnAt, String ownerNotifiedAt) {
        Store store = trialStore(TRIAL_END);
        store.setActive(false);
        store.setDeactivation(new StoreDeactivation(DeactivationReason.TRIAL_ENDED, TRIAL_END, offersWithdrawnAt,
                ownerNotifiedAt));
        return store;
    }

    private static Store switchedOffByHand(String offersWithdrawnAt) {
        Store store = new Store();
        store.setStoreId(STORE_ID);
        store.setActive(false);
        store.setDeactivation(new StoreDeactivation(DeactivationReason.MANUAL, "2026-10-19T10:00:00Z",
                offersWithdrawnAt, null));
        return store;
    }

    private void listed(Store store) {
        when(storesRepository.findAll()).thenReturn(List.of(store));
        lenient().when(storesRepository.findById(STORE_ID)).thenReturn(store);
    }

    private void deletionDue(Store store, String dueAt) {
        lenient().when(storeActivity.deletionDueAt(store)).thenReturn(Optional.of(Instant.parse(dueAt)));
    }

    @Test
    void leavesActiveStoresAlone() {
        // given
        Store store = trialStore("2026-10-25T10:00:00Z");
        when(storesRepository.findAll()).thenReturn(List.of(store));

        // when
        sweep.sweep();

        // then
        verifyNoInteractions(optimisticLockingExecutor, offerWithdrawal, trialEndedNotice, storeDeletionService);
    }

    @Test
    void deactivatesStoreWhoseTrialEndedAndTellsItsOwner() {
        // given
        Store store = trialStore(TRIAL_END);
        listed(store);
        deletionDue(store, "2026-10-26T10:00:00Z");
        when(storeActivity.status(store)).thenReturn(Optional.of(TRIAL_ENDED));

        // when
        sweep.sweep();

        // then
        assertEquals(Boolean.FALSE, store.getActive());
        StoreDeactivation deactivation = store.getDeactivation();
        assertEquals(DeactivationReason.TRIAL_ENDED, deactivation.getReason());
        assertEquals(TRIAL_END, deactivation.getDeactivatedAt());
        assertEquals(NOW.toString(), deactivation.getOffersWithdrawnAt());
        assertEquals(NOW.toString(), deactivation.getOwnerNotifiedAt());
        verify(offerWithdrawal).withdrawAll(store);
        verify(trialEndedNotice).send(store, TRIAL_ENDED);
        verifyNoInteractions(storeDeletionService);
    }

    @Test
    void stepsDoneOnceAreNotRepeated() {
        // given
        Store store = endedTrial("2026-10-12T11:00:00Z", "2026-10-12T11:00:00Z");
        listed(store);
        deletionDue(store, "2026-10-26T10:00:00Z");

        // when
        sweep.sweep();

        // then
        verifyNoInteractions(offerWithdrawal, trialEndedNotice, storeDeletionService);
        verify(storesRepository, never()).save(any());
    }

    @Test
    void failedWithdrawalIsTriedAgainByTheNextSweep() {
        // given
        Store store = endedTrial(null, null);
        listed(store);
        doThrow(new RuntimeException("marketplace down")).when(offerWithdrawal).withdrawAll(store);

        // when
        sweep.sweep();

        // then
        assertNull(store.getDeactivation().getOffersWithdrawnAt());
        verifyNoInteractions(trialEndedNotice, storeDeletionService);
        verify(storesRepository, never()).save(any());
    }

    @Test
    void failedStoreDoesNotStopTheSweep() {
        // given
        Store broken = endedTrial(null, null);
        Store other = switchedOffByHand(null);
        other.setStoreId("other-store");
        when(storesRepository.findAll()).thenReturn(List.of(broken, other));
        when(storesRepository.findById("other-store")).thenReturn(other);
        doThrow(new RuntimeException("marketplace down")).when(offerWithdrawal).withdrawAll(broken);

        // when
        sweep.sweep();

        // then
        verify(offerWithdrawal).withdrawAll(other);
        assertEquals(NOW.toString(), other.getDeactivation().getOffersWithdrawnAt());
    }

    @Test
    void failedNoticeIsSentAgainByTheNextSweep() {
        // given
        Store store = endedTrial("2026-10-12T11:00:00Z", null);
        listed(store);
        deletionDue(store, "2026-10-26T10:00:00Z");
        when(storeActivity.status(store)).thenReturn(Optional.of(TRIAL_ENDED));
        doThrow(new RuntimeException("ses down")).when(trialEndedNotice).send(store, TRIAL_ENDED);

        // when
        sweep.sweep();

        // then
        assertNull(store.getDeactivation().getOwnerNotifiedAt());
    }

    @Test
    void ownerWithoutEmailIsCountedAsTold() {
        // given
        Store store = endedTrial("2026-10-12T11:00:00Z", null);
        store.getTrial().setOwnerEmail(" ");
        listed(store);
        deletionDue(store, "2026-10-26T10:00:00Z");

        // when
        sweep.sweep();

        // then
        verifyNoInteractions(trialEndedNotice);
        assertEquals(NOW.toString(), store.getDeactivation().getOwnerNotifiedAt());
    }

    @Test
    void deletesTrialStoreOnceItsDataWasKeptForTheRetentionPeriod() {
        // given
        Store store = endedTrial("2026-10-12T11:00:00Z", "2026-10-12T11:00:00Z");
        listed(store);
        deletionDue(store, "2026-10-20T10:00:00Z");

        // when
        sweep.sweep();

        // then
        verify(storeDeletionService).deleteStore(STORE_ID, StoreDeletionService.Guard.TRIAL_ONLY);
    }

    @Test
    void withdrawsOffersBeforeDeletingTheStoreAndNeverMailsAStoreItDeletes() {
        // given
        Store store = trialStore("2026-09-01T10:00:00Z");
        listed(store);
        deletionDue(store, "2026-09-15T10:00:00Z");

        // when
        sweep.sweep();

        // then
        InOrder order = inOrder(offerWithdrawal, storeDeletionService);
        order.verify(offerWithdrawal).withdrawAll(store);
        order.verify(storeDeletionService).deleteStore(STORE_ID, StoreDeletionService.Guard.TRIAL_ONLY);
        verifyNoInteractions(trialEndedNotice);
    }

    @Test
    void storeSwitchedOffByHandLosesItsOffersButIsNeverDeletedNorMailed() {
        // given
        Store store = switchedOffByHand(null);
        listed(store);

        // when
        sweep.sweep();

        // then
        verify(offerWithdrawal).withdrawAll(store);
        assertEquals(NOW.toString(), store.getDeactivation().getOffersWithdrawnAt());
        verifyNoInteractions(trialEndedNotice, storeDeletionService);
    }

    @Test
    void trialSwitchedOffByHandKeepsItsWithdrawalWhenTheTrialEnds() {
        // given
        Store store = trialStore(TRIAL_END);
        store.setActive(false);
        store.setDeactivation(new StoreDeactivation(DeactivationReason.MANUAL, "2026-10-10T10:00:00Z",
                "2026-10-10T11:00:00Z", null));
        listed(store);
        deletionDue(store, "2026-10-26T10:00:00Z");
        when(storeActivity.status(store)).thenReturn(Optional.of(TRIAL_ENDED));

        // when
        sweep.sweep();

        // then
        assertEquals(DeactivationReason.TRIAL_ENDED, store.getDeactivation().getReason());
        assertEquals("2026-10-10T11:00:00Z", store.getDeactivation().getOffersWithdrawnAt());
        verifyNoInteractions(offerWithdrawal);
        verify(trialEndedNotice).send(store, TRIAL_ENDED);
    }

    @Test
    void storeActivatedDuringTheWithdrawalIsLeftActive() {
        // given
        Store listedStore = switchedOffByHand(null);
        Store activatedMeanwhile = new Store();
        activatedMeanwhile.setStoreId(STORE_ID);
        activatedMeanwhile.setActive(true);
        when(storesRepository.findAll()).thenReturn(List.of(listedStore));
        when(storesRepository.findById(STORE_ID)).thenReturn(activatedMeanwhile);

        // when
        sweep.sweep();

        // then
        verify(offerWithdrawal).withdrawAll(listedStore);
        verify(storesRepository, never()).save(any());
        assertNull(activatedMeanwhile.getDeactivation());
    }

    @Test
    void trialConvertedBeforeItWasMarkedIsLeftAlone() {
        // given
        Store listedStore = trialStore(TRIAL_END);
        Store converted = new Store();
        converted.setStoreId(STORE_ID);
        converted.setActive(true);
        when(storesRepository.findAll()).thenReturn(List.of(listedStore));
        when(storesRepository.findById(STORE_ID)).thenReturn(converted);

        // when
        sweep.sweep();

        // then
        verify(storesRepository, never()).save(any());
        verifyNoInteractions(offerWithdrawal, trialEndedNotice, storeDeletionService);
    }
}
