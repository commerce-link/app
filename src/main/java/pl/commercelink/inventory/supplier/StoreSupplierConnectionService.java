package pl.commercelink.inventory.supplier;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import pl.commercelink.inventory.supplier.api.SupplierProviderDescriptor;
import pl.commercelink.provider.ProviderConfigurationManager;
import pl.commercelink.provider.api.ProviderField;
import pl.commercelink.scheduling.PollingSchedule;
import pl.commercelink.stores.ConnectionMode;
import pl.commercelink.stores.FulfilmentConfiguration;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoreSupplierConnection;
import pl.commercelink.stores.SupplierSelectionForm;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
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
    private static final int IDENTITY_ATTEMPTS = 5;

    public Map<String, List<ProviderField>> configurationFields() {
        Map<String, List<ProviderField>> fields = new LinkedHashMap<>();
        for (SupplierProviderDescriptor descriptor : supplierProviderFactory.availableProviders()) {
            fields.put(descriptor.supplierInfo().name(), descriptor.configurationFields());
        }
        return fields;
    }

    public Map<String, Map<String, String>> configurationsForUI(Store store) {
        Map<String, Map<String, String>> configs = new LinkedHashMap<>();
        for (StoreSupplierConnection connection : ownConnections(store)) {
            String identity = connection.getSupplierName();
            SupplierProviderDescriptor descriptor = supplierProviderFactory.getDescriptor(identity);
            if (descriptor != null) {
                configs.put(identity, configurationManager.getConfigurationForUI(store, identity, descriptor));
            }
        }
        return configs;
    }

    // The template needs to tell "no stored configuration" apart from "stored configuration whose
    // password is masked to blank" -- getConfigurationForUI() makes both look identical, so this
    // publishes the same notion connectOrUpdate()/storedConfigFor() already use to decide
    // preservedPassword, without exposing the configuration values themselves.
    public Set<String> suppliersWithStoredConfiguration(Store store) {
        Set<String> stored = new LinkedHashSet<>();
        for (StoreSupplierConnection connection : ownConnections(store)) {
            if (hasStoredConfiguration(store, connection.getSupplierName())) {
                stored.add(connection.getSupplierName());
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

        Resolved resolved = resolveIdentity(existingStore, selection, mode);
        if (resolved.error() != null) {
            return ConnectionUpdateResult.errors(List.of(resolved.error()));
        }
        String identity = resolved.identity();

        StoreSupplierConnection edited = new StoreSupplierConnection(identity, mode,
                selection.isIncludeInPricing(), selection.isIncludeInFulfilment());
        edited.setExternalSupplierId(StringUtils.trimToNull(selection.getExternalSupplierId()));
        if (mode == ConnectionMode.OWN) {
            edited.setFeedSchedule(PollingSchedule.normalizeOrNull(selection.getFeedSchedule()));
            edited.setLabel(StringUtils.trimToNull(selection.getLabel()));
            edited.setBillingShortcut(StringUtils.trimToNull(selection.getBillingShortcut()));
        }

        List<StoreSupplierConnection> others = connectionsWithout(existingStore, identity);
        List<ErrorMessage> errors = new ArrayList<>(validator.validateLabel(
                edited, others.stream().map(SupplierLabels::labelOf).toList()));
        Map<String, Map<String, String>> config = Map.of(identity, submittedConfig);
        // Only the edited connection is validated: a broken entry belonging to another supplier
        // must not block this one.
        errors.addAll(validator.validate(
                canUseGlobal, List.of(edited), configurationFields(), config, storedConfigFor(existingStore, edited)));
        if (!errors.isEmpty()) {
            return ConnectionUpdateResult.errors(errors);
        }
        List<StoreSupplierConnection> connections = new ArrayList<>(others);
        connections.add(edited);
        return persist(existingStore, connections, config, identity);
    }

    private record Resolved(String identity, ErrorMessage error) {
        static Resolved ok(String identity) {
            return new Resolved(identity, null);
        }

        static Resolved fail(String code, Object... args) {
            return new Resolved(null, ErrorMessage.of(code, args));
        }
    }

    // Create: the form carries the adapter type in supplierName; GLOBAL keeps the type as identity
    // (one per store), OWN gets a fresh `Type-token`. Edit: the form carries the identity, which
    // never changes; a tokened identity cannot turn GLOBAL because GLOBAL identities are bare types.
    private Resolved resolveIdentity(Store existingStore, SupplierSelectionForm selection, ConnectionMode mode) {
        String identity = StringUtils.trimToNull(selection.getIdentity());
        if (identity != null) {
            StoreSupplierConnection existing = existingConfiguration(existingStore).getSupplierConnections().stream()
                    .filter(connection -> connection.getSupplierName().equals(identity))
                    .findFirst().orElse(null);
            if (existing == null) {
                return Resolved.fail("store.supplier.connection.error.not.found", identity);
            }
            // SupplierConnectionView.canSwitchMode() knows this rule only on the client side; the
            // server must enforce it too, because the endpoint is reachable without the modal.
            if (existing.getMode() == ConnectionMode.MANUAL) {
                return Resolved.fail("store.supplier.connection.error.manual.locked");
            }
            if (mode == ConnectionMode.GLOBAL && SupplierIdentity.hasToken(identity)) {
                return Resolved.fail("store.supplier.connection.error.mode.locked");
            }
            return Resolved.ok(identity);
        }
        String type = StringUtils.trimToNull(selection.getSupplierName());
        if (!isRegisteredType(type)) {
            return Resolved.fail("store.supplier.connection.error.unknown.supplier", String.valueOf(type));
        }
        if (mode == ConnectionMode.GLOBAL) {
            boolean taken = existingConfiguration(existingStore).getSupplierConnections().stream()
                    .anyMatch(connection -> connection.getSupplierName().equalsIgnoreCase(type));
            return taken ? Resolved.fail("store.supplier.connection.error.global.duplicate", type) : Resolved.ok(type);
        }
        Set<String> used = new HashSet<>();
        for (StoreSupplierConnection connection : existingConfiguration(existingStore).getSupplierConnections()) {
            used.add(connection.getSupplierName().toLowerCase(Locale.ROOT));
        }
        // Bounded: handing back a colliding candidate would have replaced the existing connection
        // (and its secret, feed and schedule) instead of adding a second instance.
        for (int attempt = 0; attempt < IDENTITY_ATTEMPTS; attempt++) {
            String candidate = newIdentity(type);
            if (!used.contains(candidate.toLowerCase(Locale.ROOT))) {
                return Resolved.ok(candidate);
            }
        }
        return Resolved.fail("store.supplier.connection.error.identity.exhausted");
    }

    /** Seam for tests that need a deterministic collision. */
    String newIdentity(String type) {
        return SupplierIdentity.newInstance(type);
    }

    // getDescriptor() resolves its argument through SupplierIdentity.typeOf, so "Kosatec-evil" would
    // hand back the Kosatec descriptor and let the operator forge an identity (GLOBAL) or blow up on
    // newInstance() (OWN). The shape check comes first; once the name is a bare type, the registry
    // lookup that follows is an exact hit, so the two together are the membership test.
    private boolean isRegisteredType(String type) {
        return SupplierIdentity.isValidTypeName(type) && supplierProviderFactory.getDescriptor(type) != null;
    }

    public ConnectionUpdateResult disconnect(Store existingStore, String identity) {
        return persist(existingStore, connectionsWithout(existingStore, identity), Map.of(), null);
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
        return ConnectionUpdateResult.ok(null, outcome.added(), outcome.removed(), outcome.rescheduled());
    }

    private ConnectionUpdateResult persist(Store existingStore, List<StoreSupplierConnection> connections,
                                           Map<String, Map<String, String>> config, String identity) {
        FulfilmentConfiguration submitted = existingConfiguration(existingStore).withConnections(connections);
        StoreSupplierConnectionPersister.PersistOutcome outcome = persister.persist(existingStore, submitted, config);
        if (!outcome.success()) {
            return ConnectionUpdateResult.errors(UPDATE_FAILED);
        }
        return ConnectionUpdateResult.ok(identity, outcome.added(), outcome.removed(), outcome.rescheduled());
    }

    // Exact match: identities are either registry type names (fixed casing) or generated tokens,
    // and the persister diffs them exactly too, so an edit never looks like an add plus a remove.
    private List<StoreSupplierConnection> connectionsWithout(Store existingStore, String identity) {
        List<StoreSupplierConnection> connections = new ArrayList<>();
        for (StoreSupplierConnection connection : existingConfiguration(existingStore).getSupplierConnections()) {
            if (!connection.getSupplierName().equals(identity)) {
                connections.add(connection);
            }
        }
        return connections;
    }

    private List<StoreSupplierConnection> ownConnections(Store store) {
        return existingConfiguration(store).getSupplierConnections().stream()
                .filter(connection -> connection.getMode() == ConnectionMode.OWN)
                .toList();
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

    public record ConnectionUpdateResult(List<ErrorMessage> errors, String identity, Set<String> added,
                                         Set<String> removed, Set<String> rescheduled) {
        static ConnectionUpdateResult errors(List<ErrorMessage> errors) {
            return new ConnectionUpdateResult(errors, null, Set.of(), Set.of(), Set.of());
        }

        static ConnectionUpdateResult ok(String identity, Set<String> added, Set<String> removed, Set<String> rescheduled) {
            return new ConnectionUpdateResult(List.of(), identity, added, removed, rescheduled);
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
