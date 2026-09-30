package pl.commercelink.stores;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import pl.commercelink.starter.dynamodb.OptimisticLockingExecutor;

import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A super admin switches any store off and on by hand. Switching off never deletes anything: only a store whose trial
 * ended is deleted, by the lifecycle sweep, and such a store comes back only as a full account.
 */
@Service
public class StoreActivationService {

    public enum Outcome { CHANGED, UNCHANGED, MISSING, TRIAL_ENDED }

    private final StoresRepository storesRepository;
    private final OptimisticLockingExecutor optimisticLockingExecutor;
    private final Clock clock;

    @Autowired
    public StoreActivationService(StoresRepository storesRepository,
                                  OptimisticLockingExecutor optimisticLockingExecutor) {
        this(storesRepository, optimisticLockingExecutor, Clock.systemUTC());
    }

    StoreActivationService(StoresRepository storesRepository, OptimisticLockingExecutor optimisticLockingExecutor,
                           Clock clock) {
        this.storesRepository = storesRepository;
        this.optimisticLockingExecutor = optimisticLockingExecutor;
        this.clock = clock;
    }

    public Outcome deactivate(String storeId) {
        return change(storeId, (store, now) -> {
            if (!store.isActive(now)) {
                return Outcome.UNCHANGED;
            }
            store.setActive(false);
            store.setDeactivation(StoreDeactivation.of(DeactivationReason.MANUAL, now));
            return Outcome.CHANGED;
        });
    }

    public Outcome activate(String storeId) {
        return change(storeId, (store, now) -> {
            if (store.isTrialExpired(now)) {
                return Outcome.TRIAL_ENDED;
            }
            if (store.isActive(now) && store.getDeactivation() == null) {
                return Outcome.UNCHANGED;
            }
            store.setActive(true);
            store.setDeactivation(null);
            return Outcome.CHANGED;
        });
    }

    private Outcome change(String storeId, Change change) {
        AtomicReference<Outcome> outcome = new AtomicReference<>();
        optimisticLockingExecutor.modifyAndSave(
                () -> storesRepository.findById(storeId),
                fresh -> outcome.set(fresh == null ? Outcome.MISSING : change.apply(fresh, clock.instant())),
                fresh -> {
                    if (outcome.get() == Outcome.CHANGED) {
                        storesRepository.save(fresh);
                    }
                });
        return outcome.get();
    }

    private interface Change {
        Outcome apply(Store store, Instant now);
    }
}
