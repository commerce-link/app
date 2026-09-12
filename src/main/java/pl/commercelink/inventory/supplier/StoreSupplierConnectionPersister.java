package pl.commercelink.inventory.supplier;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.commercelink.inventory.StoreInventoryCache;
import pl.commercelink.inventory.supplier.api.SupplierProviderDescriptor;
import pl.commercelink.provider.ProviderConfigurationManager;
import pl.commercelink.stores.ConnectionMode;
import pl.commercelink.stores.FulfilmentConfiguration;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoreSupplierConnection;
import pl.commercelink.stores.StoresRepository;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class StoreSupplierConnectionPersister {

    private final SupplierProviderFactory supplierProviderFactory;
    private final ProviderConfigurationManager configurationManager;
    private final StoreSupplierFeedScheduler feedScheduler;
    private final StoreFeedRepository storeFeedRepository;
    private final StoresRepository storesRepository;
    private final StoreInventoryCache storeInventoryCache;

    public PersistOutcome persist(Store existingStore, FulfilmentConfiguration submitted,
                                  Map<String, Map<String, String>> submittedConfig) {
        ConnectionChanges changes = computeChanges(existingStore, submitted);

        Deque<Runnable> compensations = new ArrayDeque<>();
        try {
            snapshotSecrets(existingStore, changes.affectedSecrets(), compensations);
            applyScheduleChanges(changes, compensations);
            persistConfigurations(existingStore, submitted, submittedConfig);
            saveStore(existingStore, submitted);
        } catch (RuntimeException e) {
            compensate(compensations);
            return PersistOutcome.failure();
        }

        triggerImmediateImports(changes);
        deleteRemovedFeeds(changes);
        storeInventoryCache.evict(existingStore.getStoreId());
        return PersistOutcome.success(changes.added(), changes.removed(), changes.rescheduled());
    }

    public record PersistOutcome(boolean success, Set<String> added, Set<String> removed, Set<String> rescheduled) {
        static PersistOutcome failure() {
            return new PersistOutcome(false, Set.of(), Set.of(), Set.of());
        }

        static PersistOutcome success(Set<String> added, Set<String> removed, Set<String> rescheduled) {
            return new PersistOutcome(true, Set.copyOf(added), Set.copyOf(removed), Set.copyOf(rescheduled));
        }
    }

    private ConnectionChanges computeChanges(Store existingStore, FulfilmentConfiguration submitted) {
        Map<String, String> previousSchedules = ownFeedSchedules(existingStore.getFulfilmentConfiguration());
        Map<String, String> newSchedules = ownFeedSchedules(submitted);
        Set<String> previousOwn = previousSchedules.keySet();
        Set<String> newOwn = newSchedules.keySet();
        Set<String> rescheduled = new HashSet<>();
        for (String supplier : newOwn) {
            if (previousOwn.contains(supplier)
                    && !Objects.equals(newSchedules.get(supplier), previousSchedules.get(supplier))) {
                rescheduled.add(supplier);
            }
        }
        return new ConnectionChanges(
                existingStore.getStoreId(),
                difference(newOwn, previousOwn),
                difference(previousOwn, newOwn),
                rescheduled,
                newSchedules,
                union(newOwn, previousOwn));
    }

    private void applyScheduleChanges(ConnectionChanges changes, Deque<Runnable> compensations) {
        String storeId = changes.storeId();
        for (String supplier : changes.added()) {
            rememberSchedule(storeId, supplier, compensations);
            feedScheduler.schedule(storeId, supplier, changes.newSchedules().get(supplier));
        }
        for (String supplier : changes.removed()) {
            rememberSchedule(storeId, supplier, compensations);
            feedScheduler.deleteSchedule(storeId, supplier);
        }
        for (String supplier : changes.rescheduled()) {
            rememberSchedule(storeId, supplier, compensations);
            feedScheduler.schedule(storeId, supplier, changes.newSchedules().get(supplier));
        }
    }

    private void rememberSchedule(String storeId, String supplier, Deque<Runnable> compensations) {
        Optional<String> before = feedScheduler.snapshot(storeId, supplier);
        compensations.push(() -> feedScheduler.restore(storeId, supplier, before));
    }

    private void saveStore(Store existingStore, FulfilmentConfiguration submitted) {
        existingStore.setFulfilmentConfiguration(submitted);
        storesRepository.save(existingStore);
    }

    private void triggerImmediateImports(ConnectionChanges changes) {
        for (String supplier : changes.added()) {
            try {
                feedScheduler.triggerImmediateImport(changes.storeId(), supplier);
            } catch (RuntimeException e) {
                System.err.println("Failed to trigger immediate feed import for "
                        + changes.storeId() + "/" + supplier + ": " + e.getMessage());
            }
        }
    }

    private void deleteRemovedFeeds(ConnectionChanges changes) {
        for (String supplier : changes.removed()) {
            try {
                storeFeedRepository.delete(changes.storeId(), supplier);
            } catch (RuntimeException e) {
                System.err.println("Failed to delete feed for removed supplier "
                        + changes.storeId() + "/" + supplier + ": " + e.getMessage());
            }
        }
    }

    void persistConfigurations(Store existingStore, FulfilmentConfiguration submitted, Map<String, Map<String, String>> submittedConfig) {
        Set<String> newOwnSuppliers = ownFeedSchedules(submitted).keySet();

        for (SupplierProviderDescriptor descriptor : supplierProviderFactory.availableProviders()) {
            String name = descriptor.supplierInfo().name();
            if (newOwnSuppliers.contains(name) && !descriptor.configurationFields().isEmpty()
                    && submittedConfig.containsKey(name)) {
                Map<String, String> config = submittedConfig.getOrDefault(name, Map.of());
                configurationManager.saveConfiguration(existingStore, name, descriptor, config);
            }
        }

        for (String previouslyOwn : existingStore.getOwnSupplierNames()) {
            if (!newOwnSuppliers.contains(previouslyOwn)) {
                configurationManager.deleteConfiguration(existingStore, previouslyOwn);
            }
        }
    }

    private void snapshotSecrets(Store store, Set<String> suppliers, Deque<Runnable> compensations) {
        for (String supplier : suppliers) {
            ProviderConfigurationManager.SecretSnapshot snapshot = configurationManager.snapshot(store, supplier);
            compensations.push(() -> configurationManager.restore(store, supplier, snapshot));
        }
    }

    private void compensate(Deque<Runnable> compensations) {
        while (!compensations.isEmpty()) {
            try {
                compensations.pop().run();
            } catch (RuntimeException ignored) {
            }
        }
    }

    private Map<String, String> ownFeedSchedules(FulfilmentConfiguration config) {
        Map<String, String> schedules = new HashMap<>();
        if (config == null) {
            return schedules;
        }
        for (StoreSupplierConnection connection : config.getSupplierConnections()) {
            if (connection.getMode() == ConnectionMode.OWN) {
                schedules.put(connection.getSupplierName(), connection.getFeedSchedule());
            }
        }
        return schedules;
    }

    private Set<String> difference(Set<String> from, Set<String> remove) {
        Set<String> result = new HashSet<>(from);
        result.removeAll(remove);
        return result;
    }

    private Set<String> union(Set<String> first, Set<String> second) {
        Set<String> result = new HashSet<>(first);
        result.addAll(second);
        return result;
    }

    private record ConnectionChanges(String storeId,
                                     Set<String> added,
                                     Set<String> removed,
                                     Set<String> rescheduled,
                                     Map<String, String> newSchedules,
                                     Set<String> affectedSecrets) {
    }
}
