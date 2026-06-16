package pl.commercelink.inventory.supplier;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.commercelink.inventory.supplier.api.SupplierConfigField;
import pl.commercelink.inventory.supplier.api.SupplierDescriptor;
import pl.commercelink.stores.ConnectionMode;
import pl.commercelink.stores.FulfilmentConfiguration;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoreSupplierConnection;
import pl.commercelink.stores.SupplierSelectionForm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StoreSupplierConnectionService {

    private final SupplierRegistry supplierRegistry;
    private final SupplierConfigurationManager configurationManager;
    private final SupplierConnectionValidator validator;

    public Map<String, List<SupplierConfigField>> configurationFields() {
        Map<String, List<SupplierConfigField>> fields = new LinkedHashMap<>();
        for (SupplierDescriptor descriptor : supplierRegistry.getAllDescriptors()) {
            fields.put(descriptor.supplierInfo().name(), descriptor.configurationFields());
        }
        return fields;
    }

    public Map<String, Map<String, String>> configurationsForUI(Store store) {
        Map<String, Map<String, String>> configs = new HashMap<>();
        configurationFields().forEach((name, fields) ->
                configs.put(name, configurationManager.getConfigurationForUI(store, name, fields)));
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
                    connection != null ? connection.getMode() : ConnectionMode.GLOBAL));
        }
        return selections;
    }

    public List<String> apply(Store existingStore, FulfilmentConfiguration submitted,
                              List<SupplierSelectionForm> selections,
                              Map<String, Map<String, String>> submittedConfig, boolean isSuperAdmin) {
        boolean canUseGlobal = resolveCanUseGlobalSuppliers(existingStore, submitted.isCanUseGlobalSuppliers(), isSuperAdmin);
        submitted.setCanUseGlobalSuppliers(canUseGlobal);
        submitted.setSupplierConnections(buildConnections(selections, canUseGlobal));

        List<String> errors = validate(existingStore, submitted, submittedConfig);
        if (!errors.isEmpty()) {
            return errors;
        }
        persistConfigurations(existingStore, submitted, submittedConfig);
        return errors;
    }

    boolean resolveCanUseGlobalSuppliers(Store existingStore, boolean submittedCanUseGlobal, boolean isSuperAdmin) {
        boolean storedCanUseGlobal = existingStore.getFulfilmentConfiguration() == null
                || existingStore.getFulfilmentConfiguration().isCanUseGlobalSuppliers();
        return isSuperAdmin ? submittedCanUseGlobal : storedCanUseGlobal;
    }

    List<StoreSupplierConnection> buildConnections(List<SupplierSelectionForm> selections, boolean canUseGlobal) {
        List<StoreSupplierConnection> connections = new ArrayList<>();
        for (SupplierSelectionForm selection : selections) {
            if (selection.isEnabled()) {
                ConnectionMode mode = canUseGlobal
                        ? (selection.getMode() != null ? selection.getMode() : ConnectionMode.GLOBAL)
                        : ConnectionMode.OWN;
                connections.add(new StoreSupplierConnection(selection.getSupplierName(), mode));
            }
        }
        return connections;
    }

    List<String> validate(Store existingStore, FulfilmentConfiguration submitted, Map<String, Map<String, String>> submittedConfig) {
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

    void persistConfigurations(Store existingStore, FulfilmentConfiguration submitted, Map<String, Map<String, String>> submittedConfig) {
        Set<String> newOwnSuppliers = ownSupplierNames(submitted);

        for (SupplierDescriptor descriptor : supplierRegistry.getAllDescriptors()) {
            String name = descriptor.supplierInfo().name();
            if (newOwnSuppliers.contains(name) && !descriptor.configurationFields().isEmpty()) {
                Map<String, String> config = submittedConfig.getOrDefault(name, Map.of());
                configurationManager.saveConfiguration(existingStore, name, descriptor.configurationFields(), config);
            }
        }

        for (String previouslyOwn : existingStore.getOwnSupplierNames()) {
            if (!newOwnSuppliers.contains(previouslyOwn)) {
                configurationManager.deleteConfiguration(existingStore, previouslyOwn);
            }
        }
    }

    private Set<String> ownSupplierNames(FulfilmentConfiguration config) {
        return config.getSupplierConnections().stream()
                .filter(connection -> connection.getMode() == ConnectionMode.OWN)
                .map(StoreSupplierConnection::getSupplierName)
                .collect(Collectors.toSet());
    }
}
