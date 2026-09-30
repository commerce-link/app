package pl.commercelink.stores;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * Whether a store may do work. Every job, listener and entry point that acts for a store asks here first, so an
 * inactive store keeps its data but nothing runs, sells or sends anything on its behalf.
 */
@Component
public class StoreActivity {

    private final StoresRepository storesRepository;
    private final Duration trialRetention;
    private final Clock clock;

    @Autowired
    public StoreActivity(StoresRepository storesRepository,
                         @Value("${app.registration.trial-retention-days}") int trialRetentionDays) {
        this(storesRepository, trialRetentionDays, Clock.systemUTC());
    }

    StoreActivity(StoresRepository storesRepository, int trialRetentionDays, Clock clock) {
        this.storesRepository = storesRepository;
        this.trialRetention = Duration.ofDays(trialRetentionDays);
        this.clock = clock;
    }

    public boolean isActive(Store store) {
        return store != null && store.isActive(clock.instant());
    }

    /** A store that no longer exists does no work either. */
    public boolean isActive(String storeId) {
        return storeId != null && isActive(storesRepository.findById(storeId));
    }

    /** When the data of a trial store is deleted if the trial is not converted to a full account before. */
    public Optional<Instant> deletionDueAt(Store store) {
        if (store == null || store.getTrial() == null) {
            return Optional.empty();
        }
        return store.getTrial().expiresAtInstant().map(end -> end.plus(trialRetention));
    }

    public Optional<DeactivationStatus> status(Store store) {
        Instant now = clock.instant();
        if (store == null || store.isActive(now)) {
            return Optional.empty();
        }
        if (store.isTrialExpired(now)) {
            LocalDate deactivatedOn = localDate(store.getTrial().expiresAtInstant().orElseThrow());
            LocalDate deletionOn = localDate(deletionDueAt(store).orElseThrow());
            long daysLeft = Math.max(0, ChronoUnit.DAYS.between(localDate(now), deletionOn));
            return Optional.of(new DeactivationStatus(DeactivationReason.TRIAL_ENDED, deactivatedOn, deletionOn, daysLeft));
        }
        return Optional.of(new DeactivationStatus(DeactivationReason.MANUAL, manualDeactivationDay(store), null, 0));
    }

    private LocalDate manualDeactivationDay(Store store) {
        StoreDeactivation deactivation = store.getDeactivation();
        if (deactivation == null || deactivation.getDeactivatedAt() == null) {
            return null;
        }
        try {
            return localDate(Instant.parse(deactivation.getDeactivatedAt()));
        } catch (DateTimeException e) {
            return null;
        }
    }

    private static LocalDate localDate(Instant instant) {
        return instant.atZone(StoreTrialService.TRIAL_ZONE).toLocalDate();
    }
}
