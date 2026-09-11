package pl.commercelink.inventory.supplier;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.commercelink.inventory.supplier.api.SupplierProviderDescriptor;
import pl.commercelink.provider.ProviderConfigurationManager;
import pl.commercelink.provider.api.ProviderField;
import pl.commercelink.stores.ConnectionMode;
import pl.commercelink.stores.FulfilmentConfiguration;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoreSupplierConnection;
import pl.commercelink.stores.SupplierSelectionForm;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class StoreSupplierConnectionService {

    private final SupplierProviderFactory supplierProviderFactory;
    private final ProviderConfigurationManager configurationManager;
    private final SupplierConnectionValidator validator;
    private final StoreSupplierConnectionPersister persister;

    private static final List<ErrorMessage> UPDATE_FAILED = List.of(ErrorMessage.of("store.supplier.connection.error.update.failed"));

    public Map<String, List<ProviderField>> configurationFields() {
        Map<String, List<ProviderField>> fields = new LinkedHashMap<>();
        for (SupplierProviderDescriptor descriptor : supplierProviderFactory.availableProviders()) {
            fields.put(descriptor.supplierInfo().name(), descriptor.configurationFields());
        }
        return fields;
    }

    public Map<String, Map<String, String>> configurationsForUI(Store store) {
        Map<String, Map<String, String>> configs = new LinkedHashMap<>();
        for (SupplierProviderDescriptor descriptor : supplierProviderFactory.availableProviders()) {
            String name = descriptor.supplierInfo().name();
            configs.put(name, configurationManager.getConfigurationForUI(store, name, descriptor));
        }
        return configs;
    }

    // The template needs to tell "no stored configuration" apart from "stored configuration whose
    // password is masked to blank" -- getConfigurationForUI() makes both look identical, so this
    // publishes the same notion connectOrUpdate()/storedConfigFor() already use to decide
    // preservedPassword, without exposing the configuration values themselves.
    public Set<String> suppliersWithStoredConfiguration(Store store) {
        Set<String> stored = new LinkedHashSet<>();
        for (SupplierProviderDescriptor descriptor : supplierProviderFactory.availableProviders()) {
            String name = descriptor.supplierInfo().name();
            if (hasStoredConfiguration(store, name)) {
                stored.add(name);
            }
        }
        return stored;
    }

    public ConnectionUpdateResult connectOrUpdate(Store existingStore, SupplierSelectionForm selection,
                                                  Map<String, String> submittedConfig) {
        boolean canUseGlobal = existingStore.canUseGlobalSuppliers();
        ConnectionMode mode = canUseGlobal
                ? (selection.getMode() != null ? selection.getMode() : ConnectionMode.GLOBAL)
                : ConnectionMode.OWN;
        StoreSupplierConnection edited = new StoreSupplierConnection(
                selection.getSupplierName(), mode,
                selection.isIncludeInPricing(), selection.isIncludeInFulfilment());

        List<StoreSupplierConnection> connections = connectionsWithout(existingStore, selection.getSupplierName());
        connections.add(edited);

        Map<String, Map<String, String>> config = Map.of(selection.getSupplierName(), submittedConfig);
        // Only the edited connection is validated: a broken entry belonging to another supplier
        // must not block this one.
        List<ErrorMessage> errors = validator.validate(
                canUseGlobal, List.of(edited), configurationFields(), config, storedConfigFor(existingStore, edited));
        if (!errors.isEmpty()) {
            return ConnectionUpdateResult.errors(errors);
        }
        return persist(existingStore, connections, config);
    }

    public ConnectionUpdateResult disconnect(Store existingStore, String supplierName) {
        return persist(existingStore, connectionsWithout(existingStore, supplierName), Map.of());
    }

    public ConnectionUpdateResult applyStoreSettings(Store existingStore, FulfilmentConfiguration submitted,
                                                     boolean isSuperAdmin) {
        FulfilmentConfiguration existing = existingConfiguration(existingStore);
        submitted.setEnabledProductGroups(existing.getEnabledProductGroups());
        if (submitted.getEnabledCategories() == null) {
            submitted.setEnabledCategories(existing.getEnabledCategories());
        }
        submitted.setCanUseGlobalSuppliers(
                resolveCanUseGlobalSuppliers(existingStore, submitted.isCanUseGlobalSuppliers(), isSuperAdmin));
        submitted.setInventoryCacheTtlMinutes(
                resolveInventoryCacheTtlMinutes(existingStore, submitted.getInventoryCacheTtlMinutes(), isSuperAdmin));
        // Connections have their own per-supplier endpoints; this path must leave them alone.
        submitted.setSupplierConnections(new ArrayList<>(existing.getSupplierConnections()));

        StoreSupplierConnectionPersister.PersistOutcome outcome = persister.persist(existingStore, submitted, Map.of());
        if (!outcome.success()) {
            return ConnectionUpdateResult.errors(UPDATE_FAILED);
        }
        return ConnectionUpdateResult.ok(outcome.added(), outcome.removed());
    }

    private ConnectionUpdateResult persist(Store existingStore, List<StoreSupplierConnection> connections,
                                           Map<String, Map<String, String>> config) {
        FulfilmentConfiguration submitted = existingConfiguration(existingStore).withConnections(connections);
        StoreSupplierConnectionPersister.PersistOutcome outcome = persister.persist(existingStore, submitted, config);
        if (!outcome.success()) {
            return ConnectionUpdateResult.errors(UPDATE_FAILED);
        }
        return ConnectionUpdateResult.ok(outcome.added(), outcome.removed());
    }

    // Case-insensitive on purpose: identities here always come from the supplier registry, which
    // is also what StoreSupplierConnectionPersister compares by exact (case-sensitive) match when
    // it works out what was added and removed. The two must keep agreeing on case, or an edit
    // whose casing differs from what is stored would look like an add plus a remove and delete
    // the supplier's secret.
    private List<StoreSupplierConnection> connectionsWithout(Store existingStore, String supplierName) {
        List<StoreSupplierConnection> connections = new ArrayList<>();
        for (StoreSupplierConnection connection : existingConfiguration(existingStore).getSupplierConnections()) {
            if (!connection.getSupplierName().equalsIgnoreCase(supplierName)) {
                connections.add(connection);
            }
        }
        return connections;
    }

    // A store without any fulfilment configuration yet (e.g. connecting its first supplier)
    // legitimately has a null getFulfilmentConfiguration().
    private FulfilmentConfiguration existingConfiguration(Store existingStore) {
        FulfilmentConfiguration config = existingStore.getFulfilmentConfiguration();
        return config != null ? config : new FulfilmentConfiguration();
    }

    private Set<String> storedConfigFor(Store existingStore, StoreSupplierConnection connection) {
        if (connection.getMode() != ConnectionMode.OWN
                || !hasStoredConfiguration(existingStore, connection.getSupplierName())) {
            return Set.of();
        }
        return Set.of(connection.getSupplierName());
    }

    private boolean hasStoredConfiguration(Store existingStore, String supplierName) {
        return !configurationManager.loadConfiguration(existingStore, supplierName).isEmpty();
    }

    public record ConnectionUpdateResult(List<ErrorMessage> errors, Set<String> added, Set<String> removed) {
        static ConnectionUpdateResult errors(List<ErrorMessage> errors) {
            return new ConnectionUpdateResult(errors, Set.of(), Set.of());
        }

        static ConnectionUpdateResult ok(Set<String> added, Set<String> removed) {
            return new ConnectionUpdateResult(List.of(), added, removed);
        }

        public boolean hasErrors() {
            return !errors.isEmpty();
        }
    }

    boolean resolveCanUseGlobalSuppliers(Store existingStore, boolean submittedCanUseGlobal, boolean isSuperAdmin) {
        return isSuperAdmin ? submittedCanUseGlobal : existingStore.canUseGlobalSuppliers();
    }

    Integer resolveInventoryCacheTtlMinutes(Store existingStore, Integer submittedTtl, boolean isSuperAdmin) {
        return isSuperAdmin ? submittedTtl : existingStore.getInventoryCacheTtlMinutes().orElse(null);
    }
}
