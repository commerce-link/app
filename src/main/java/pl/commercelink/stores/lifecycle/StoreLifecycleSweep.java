package pl.commercelink.stores.lifecycle;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import pl.commercelink.marketplace.MarketplaceOfferWithdrawal;
import pl.commercelink.starter.dynamodb.OptimisticLockingExecutor;
import pl.commercelink.stores.DeactivationReason;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoreActivity;
import pl.commercelink.stores.StoreDeactivation;
import pl.commercelink.stores.StoreDeletionService;
import pl.commercelink.stores.StoresRepository;

import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Takes inactive stores through the steps that follow their deactivation: a trial that ended deactivates its store,
 * the offers of a deactivated store are withdrawn from the marketplaces, the owner of an ended trial is told, and the
 * store is deleted once its data was kept for the retention period. Each step is recorded on the store, so a failed
 * step is tried again by the next sweep and a step done once is not repeated.
 */
@Slf4j
@Component
public class StoreLifecycleSweep {

    private final StoresRepository storesRepository;
    private final StoreActivity storeActivity;
    private final OptimisticLockingExecutor optimisticLockingExecutor;
    private final MarketplaceOfferWithdrawal offerWithdrawal;
    private final TrialEndedNotice trialEndedNotice;
    private final StoreDeletionService storeDeletionService;
    private final Clock clock;

    @Autowired
    StoreLifecycleSweep(StoresRepository storesRepository, StoreActivity storeActivity,
                        OptimisticLockingExecutor optimisticLockingExecutor, MarketplaceOfferWithdrawal offerWithdrawal,
                        TrialEndedNotice trialEndedNotice, StoreDeletionService storeDeletionService) {
        this(storesRepository, storeActivity, optimisticLockingExecutor, offerWithdrawal, trialEndedNotice,
                storeDeletionService, Clock.systemUTC());
    }

    StoreLifecycleSweep(StoresRepository storesRepository, StoreActivity storeActivity,
                        OptimisticLockingExecutor optimisticLockingExecutor, MarketplaceOfferWithdrawal offerWithdrawal,
                        TrialEndedNotice trialEndedNotice, StoreDeletionService storeDeletionService, Clock clock) {
        this.storesRepository = storesRepository;
        this.storeActivity = storeActivity;
        this.optimisticLockingExecutor = optimisticLockingExecutor;
        this.offerWithdrawal = offerWithdrawal;
        this.trialEndedNotice = trialEndedNotice;
        this.storeDeletionService = storeDeletionService;
        this.clock = clock;
    }

    public void sweep() {
        for (Store store : storesRepository.findAll()) {
            try {
                advance(store);
            } catch (RuntimeException e) {
                log.error("Lifecycle step of inactive store {} failed, the next sweep tries again", store.getStoreId(), e);
            }
        }
    }

    private void advance(Store listed) {
        Instant now = clock.instant();
        if (listed.isActive(now)) {
            return;
        }
        Store store = listed;
        if (store.isTrialExpired(now) && !endedTrial(store)) {
            store = markTrialEnded(store.getStoreId());
        }
        if (store == null || store.getDeactivation() == null) {
            return;
        }
        if (store.getDeactivation().getOffersWithdrawnAt() == null) {
            offerWithdrawal.withdrawAll(store);
            store = record(store.getStoreId(), deactivation -> deactivation.setOffersWithdrawnAt(now.toString()));
            if (store == null) {
                return;
            }
        }
        // The offers go first: once the store is deleted, nothing is left that could take them off sale.
        if (store.isTrialExpired(now) && isDeletionDue(store, now)) {
            storeDeletionService.deleteStore(store.getStoreId(), StoreDeletionService.Guard.TRIAL_ONLY);
            return;
        }
        if (endedTrial(store) && store.getDeactivation().getOwnerNotifiedAt() == null) {
            notifyOwner(store);
            record(store.getStoreId(), deactivation -> deactivation.setOwnerNotifiedAt(now.toString()));
        }
    }

    private void notifyOwner(Store store) {
        String ownerEmail = store.getTrial().getOwnerEmail();
        if (ownerEmail == null || ownerEmail.isBlank()) {
            log.warn("Trial of store {} ended without an owner e-mail to tell", store.getStoreId());
            return;
        }
        trialEndedNotice.send(store, storeActivity.status(store).orElseThrow());
    }

    private boolean isDeletionDue(Store store, Instant now) {
        return storeActivity.deletionDueAt(store).map(due -> !now.isBefore(due)).orElse(false);
    }

    private static boolean endedTrial(Store store) {
        return store.getDeactivation() != null && store.getDeactivation().getReason() == DeactivationReason.TRIAL_ENDED;
    }

    /** A store switched off by hand before its trial ended keeps its withdrawal, the trial end takes over the rest. */
    private Store markTrialEnded(String storeId) {
        return update(storeId, fresh -> fresh.isTrialExpired(clock.instant()) && !endedTrial(fresh), fresh -> {
            StoreDeactivation previous = fresh.getDeactivation();
            fresh.setActive(false);
            fresh.setDeactivation(new StoreDeactivation(DeactivationReason.TRIAL_ENDED, fresh.getTrial().getExpiresAt(),
                    previous == null ? null : previous.getOffersWithdrawnAt(), null));
        });
    }

    /** Skipped for a store activated or converted meanwhile: the step no longer applies to it. */
    private Store record(String storeId, Consumer<StoreDeactivation> step) {
        return update(storeId,
                fresh -> fresh.getDeactivation() != null && !fresh.isActive(clock.instant()),
                fresh -> step.accept(fresh.getDeactivation()));
    }

    /** The store as saved, or null when it is gone or the change no longer applies to it. */
    private Store update(String storeId, Predicate<Store> applies, Consumer<Store> change) {
        AtomicReference<Store> changed = new AtomicReference<>();
        optimisticLockingExecutor.modifyAndSave(
                () -> storesRepository.findById(storeId),
                fresh -> {
                    boolean applicable = fresh != null && applies.test(fresh);
                    if (applicable) {
                        change.accept(fresh);
                    }
                    changed.set(applicable ? fresh : null);
                },
                fresh -> {
                    if (changed.get() != null) {
                        storesRepository.save(fresh);
                    }
                });
        return changed.get();
    }
}
