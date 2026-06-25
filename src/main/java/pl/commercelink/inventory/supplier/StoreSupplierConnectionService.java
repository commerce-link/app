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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class StoreSupplierConnectionService {

    private final SupplierRegistry supplierRegistry;
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

    public List<SupplierSelectionForm> selectionsFor(Store store) {
        List<StoreSupplierConnection> existing = store.getFulfilmentConfiguration().getSupplierConnections();
        List<SupplierSelectionForm> selections = new ArrayList<>();
        for (String name : supplierRegistry.getExternalSupplierNames()) {
            StoreSupplierConnection connection = existing.stream()
                    .filter(c -> c.getSupplierName().equals(name))
                    .findFirst()
                    .orElse(null);
            selections.add(new SupplierSelectionForm(
                    name,
                    connection != null,
                    connection != null ? connection.getMode() : ConnectionMode.GLOBAL,
                    connection == null || connection.isIncludeInPricing(),
                    connection == null || connection.isIncludeInFulfilment()));
        }
        return selections;
    }

    public ConnectionUpdateResult apply(Store existingStore, FulfilmentConfiguration submitted,
                                        List<SupplierSelectionForm> selections,
                                        Map<String, Map<String, String>> submittedConfig, boolean isSuperAdmin) {
        prepareSubmittedConfiguration(existingStore, submitted, selections, isSuperAdmin);

        List<ErrorMessage> errors = validate(existingStore, submitted, submittedConfig);
        if (!errors.isEmpty()) {
            return ConnectionUpdateResult.errors(errors);
        }

        StoreSupplierConnectionPersister.PersistOutcome outcome =
                persister.persist(existingStore, submitted, submittedConfig);
        if (!outcome.success()) {
            return ConnectionUpdateResult.errors(UPDATE_FAILED);
        }
        return ConnectionUpdateResult.ok(outcome.added(), outcome.removed());
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

    private void prepareSubmittedConfiguration(Store existingStore, FulfilmentConfiguration submitted,
                                               List<SupplierSelectionForm> selections, boolean isSuperAdmin) {
        boolean canUseGlobal = resolveCanUseGlobalSuppliers(existingStore, submitted.isCanUseGlobalSuppliers(), isSuperAdmin);
        submitted.setCanUseGlobalSuppliers(canUseGlobal);
        submitted.setSupplierConnections(buildConnections(selections, canUseGlobal));
        submitted.setInventoryCacheTtlMinutes(
                resolveInventoryCacheTtlMinutes(existingStore, submitted.getInventoryCacheTtlMinutes(), isSuperAdmin));
    }

    boolean resolveCanUseGlobalSuppliers(Store existingStore, boolean submittedCanUseGlobal, boolean isSuperAdmin) {
        return isSuperAdmin ? submittedCanUseGlobal : existingStore.canUseGlobalSuppliers();
    }

    Integer resolveInventoryCacheTtlMinutes(Store existingStore, Integer submittedTtl, boolean isSuperAdmin) {
        return isSuperAdmin ? submittedTtl : existingStore.getInventoryCacheTtlMinutes().orElse(null);
    }

    List<StoreSupplierConnection> buildConnections(List<SupplierSelectionForm> selections, boolean canUseGlobal) {
        List<StoreSupplierConnection> connections = new ArrayList<>();
        for (SupplierSelectionForm selection : selections) {
            if (selection.isEnabled()) {
                ConnectionMode mode = canUseGlobal
                        ? (selection.getMode() != null ? selection.getMode() : ConnectionMode.GLOBAL)
                        : ConnectionMode.OWN;
                connections.add(new StoreSupplierConnection(
                        selection.getSupplierName(), mode,
                        selection.isIncludeInPricing(), selection.isIncludeInFulfilment()));
            }
        }
        return connections;
    }

    List<ErrorMessage> validate(Store existingStore, FulfilmentConfiguration submitted, Map<String, Map<String, String>> submittedConfig) {
        Set<String> suppliersWithStoredConfig = new HashSet<>();
        for (StoreSupplierConnection connection : submitted.getSupplierConnections()) {
            if (connection.getMode() == ConnectionMode.OWN
                    && !configurationManager.loadConfiguration(existingStore, connection.getSupplierName()).isEmpty()) {
                suppliersWithStoredConfig.add(connection.getSupplierName());
            }
        }
        return validator.validate(
                submitted.isCanUseGlobalSuppliers(), submitted.getSupplierConnections(),
                configurationFields(), submittedConfig, suppliersWithStoredConfig);
    }
}
