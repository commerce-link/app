package pl.commercelink.stores;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import pl.commercelink.starter.dynamodb.OptimisticLockingExecutor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
public class StoreTrialService {

    static final ZoneId TRIAL_ZONE = ZoneId.of("Europe/Warsaw");

    private final StoresRepository storesRepository;
    private final OptimisticLockingExecutor optimisticLockingExecutor;
    private final Clock clock;

    @Autowired
    public StoreTrialService(StoresRepository storesRepository, OptimisticLockingExecutor optimisticLockingExecutor) {
        this(storesRepository, optimisticLockingExecutor, Clock.systemUTC());
    }

    StoreTrialService(StoresRepository storesRepository, OptimisticLockingExecutor optimisticLockingExecutor,
                      Clock clock) {
        this.storesRepository = storesRepository;
        this.optimisticLockingExecutor = optimisticLockingExecutor;
        this.clock = clock;
    }

    public Optional<TrialStatus> status(Store store) {
        if (store == null || store.getTrial() == null) {
            return Optional.empty();
        }
        TrialPeriod trial = store.getTrial();
        Optional<Instant> end = trial.expiresAtInstant();
        if (end.isEmpty()) {
            log.error("Trial of store {} has an unreadable end date '{}', so it does not hold the store back",
                    store.getStoreId(), trial.getExpiresAt());
            return Optional.empty();
        }
        Instant now = clock.instant();
        LocalDate endsOn = end.get().atZone(TRIAL_ZONE).toLocalDate();
        long daysLeft = Math.max(0, ChronoUnit.DAYS.between(now.atZone(TRIAL_ZONE).toLocalDate(), endsOn));
        return Optional.of(new TrialStatus(endsOn, daysLeft, trial.isExpired(now)));
    }

    public boolean convertToFullAccount(String storeId) {
        AtomicBoolean converted = new AtomicBoolean();
        optimisticLockingExecutor.modifyAndSave(
                () -> storesRepository.findById(storeId),
                fresh -> {
                    boolean onTrial = fresh != null && fresh.getTrial() != null;
                    if (onTrial) {
                        fresh.setTrial(null);
                        fresh.setActive(true);
                        fresh.setDeactivation(null);
                    }
                    converted.set(onTrial);
                },
                fresh -> {
                    if (converted.get()) {
                        storesRepository.save(fresh);
                    }
                });
        return converted.get();
    }
}
