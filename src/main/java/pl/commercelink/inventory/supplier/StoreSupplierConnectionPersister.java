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
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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
        return PersistOutcome.success(changes.added(), changes.removed());
    }

    public record PersistOutcome(boolean success, Set<String> added, Set<String> removed) {
        static PersistOutcome failure() {
            return new PersistOutcome(false, Set.of(), Set.of());
        }

        static PersistOutcome success(Set<String> added, Set<String> removed) {
            return new PersistOutcome(true, Set.copyOf(added), Set.copyOf(removed));
        }
    }

    private ConnectionChanges computeChanges(Store existingStore, FulfilmentConfiguration submitted) {
        Set<String> previousOwn = new HashSet<>(existingStore.getOwnSupplierNames());
        Set<String> newOwn = ownSupplierNames(submitted);
        return new ConnectionChanges(
                existingStore.getStoreId(),
                difference(newOwn, previousOwn),
                difference(previousOwn, newOwn),
                union(newOwn, previousOwn));
    }

    private void applyScheduleChanges(ConnectionChanges changes, Deque<Runnable> compensations) {
        for (String supplier : changes.added()) {
            feedScheduler.createSchedule(changes.storeId(), supplier);
            compensations.push(() -> feedScheduler.deleteSchedule(changes.storeId(), supplier));
        }
        for (String supplier : changes.removed()) {
            feedScheduler.deleteSchedule(changes.storeId(), supplier);
            compensations.push(() -> feedScheduler.createSchedule(changes.storeId(), supplier));
        }
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
        Set<String> newOwnSuppliers = ownSupplierNames(submitted);

        for (SupplierProviderDescriptor descriptor : supplierProviderFactory.availableProviders()) {
            String name = descriptor.supplierInfo().name();
            if (newOwnSuppliers.contains(name) && !descriptor.configurationFields().isEmpty()) {
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

    private Set<String> ownSupplierNames(FulfilmentConfiguration config) {
        return config.getSupplierConnections().stream()
                .filter(connection -> connection.getMode() == ConnectionMode.OWN)
                .map(StoreSupplierConnection::getSupplierName)
                .collect(Collectors.toSet());
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

    private record ConnectionChanges(String storeId, Set<String> added, Set<String> removed, Set<String> affectedSecrets) {
    }
}
