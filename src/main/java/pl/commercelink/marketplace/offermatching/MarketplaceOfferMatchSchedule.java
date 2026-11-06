package pl.commercelink.marketplace.offermatching;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.commercelink.starter.dynamodb.OptimisticLockingExecutor;
import pl.commercelink.stores.MarketplaceIntegration;
import pl.commercelink.stores.StoresRepository;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

/**
 * Whether a marketplace connection's offers are matched every night ({@link MarketplaceOfferMatchSweep}). Saved on a
 * fresh copy of the store, so a change made meanwhile elsewhere (an import's lastFetchedAt, another setting) is kept.
 */
@Component
@RequiredArgsConstructor
public class MarketplaceOfferMatchSchedule {

    private final StoresRepository storesRepository;
    private final OptimisticLockingExecutor optimisticLockingExecutor;

    /**
     * The operator's switch. True when the setting was changed and saved. A store or connection that is gone is left as
     * it is; an unchanged setting is not saved again. Switching off a connection that never had the setting saves it as
     * off, so a later manual run does not switch it on.
     */
    public boolean set(String storeId, String marketplace, boolean scheduled) {
        return change(storeId, marketplace, current -> !Boolean.valueOf(scheduled).equals(current), scheduled);
    }

    /**
     * The operator's manual run: switches daily matching on only for a connection that never had the setting, so an
     * operator who switched it off keeps it off. True when it was switched on by this call.
     */
    public boolean enableIfNeverSet(String storeId, String marketplace) {
        return change(storeId, marketplace, Objects::isNull, true);
    }

    private boolean change(String storeId, String marketplace, Predicate<Boolean> applies, boolean scheduled) {
        AtomicBoolean changed = new AtomicBoolean();
        optimisticLockingExecutor.modifyAndSave(
                () -> storesRepository.findById(storeId),
                fresh -> {
                    MarketplaceIntegration integration = fresh == null ? null
                            : fresh.getMarketplaceIntegration(marketplace);
                    changed.set(integration != null && applies.test(integration.getOfferMatchingScheduled()));
                    if (changed.get()) {
                        integration.setOfferMatchingScheduled(scheduled);
                    }
                },
                fresh -> {
                    if (changed.get()) {
                        storesRepository.save(fresh);
                    }
                });
        return changed.get();
    }
}
