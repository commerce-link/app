package pl.commercelink.web;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;
import pl.commercelink.inventory.supplier.ErrorMessage;
import pl.commercelink.inventory.supplier.StoreSupplierConnectionService;
import pl.commercelink.inventory.supplier.SupplierConnectionView;
import pl.commercelink.inventory.supplier.SupplierConnectionViewFactory;
import pl.commercelink.inventory.supplier.SupplierIdentity;
import pl.commercelink.inventory.supplier.SupplierProviderFactory;
import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.inventory.supplier.api.SupplierProviderDescriptor;
import pl.commercelink.inventory.supplier.manual.ManualSupplierService;
import pl.commercelink.provider.api.ProviderField;
import pl.commercelink.stores.ConnectionMode;
import pl.commercelink.stores.FulfilmentConfiguration;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoreSupplierConnection;
import pl.commercelink.web.dtos.SupplierSettingsForm;
import pl.commercelink.web.settings.SupplierView;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * What the suppliers settings pages need to know about the store's suppliers, and saving them. Integrations go through
 * {@link StoreSupplierConnectionService} (which also keeps the feed schedules and secrets in step), price lists through
 * {@link ManualSupplierService}. Both services still check their own rules; their errors are mapped onto the page's
 * fields so they show where the operator can fix them.
 */
@Slf4j
@Component
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
class SupplierConnections {

    // Service error codes and the field (and the argument-free message) they belong to on the supplier page.
    private static final Map<String, FieldError> SERVICE_ERRORS = Map.ofEntries(
            Map.entry("store.supplier.connection.error.label.required", new FieldError("label", "store.suppliers.label.required")),
            Map.entry("store.supplier.connection.error.label.too.long", new FieldError("label", "store.suppliers.label.tooLong")),
            Map.entry("store.supplier.connection.error.label.reserved", new FieldError("label", "store.suppliers.label.reserved")),
            Map.entry("store.supplier.connection.error.label.taken", new FieldError("label", "store.suppliers.label.taken")),
            Map.entry("store.manual.error.name.invalid", new FieldError("label", "store.suppliers.label.required")),
            Map.entry("store.manual.error.name.taken", new FieldError("label", "store.suppliers.label.taken")),
            Map.entry("store.supplier.connection.error.invalid.schedule", new FieldError("feedSchedule", "store.suppliers.schedule.invalid")),
            Map.entry("store.supplier.connection.error.schedule.too.frequent", new FieldError("feedSchedule", "store.suppliers.schedule.tooFrequent")),
            Map.entry("store.supplier.connection.error.unknown.supplier", new FieldError("providerName", "store.suppliers.provider.required")),
            Map.entry("store.supplier.connection.error.global.duplicate", new FieldError("mode", "store.suppliers.mode.globalDuplicate")),
            Map.entry("store.supplier.connection.error.global.own.exists", new FieldError("mode", "store.suppliers.mode.ownExists")),
            Map.entry("store.supplier.connection.error.mode.locked", new FieldError("mode", "store.suppliers.mode.locked")),
            Map.entry("store.supplier.connection.error.requires.own.connection", new FieldError("mode", "store.suppliers.mode.ownRequired")));

    private final StoreSupplierConnectionService connectionService;
    private final ManualSupplierService manualSupplierService;
    private final SupplierConnectionViewFactory viewFactory;
    private final SupplierProviderFactory providerFactory;
    private final SupplierRegistry supplierRegistry;
    private final MessageSource messageSource;

    @Value("${scheduling.min-interval-minutes}")
    private int minIntervalMinutes;

    record FieldError(String field, String messageKey) {
    }

    /** Field errors (field id to message key) or a failure that belongs to no field, e.g. an AWS error. */
    record SaveResult(Map<String, String> errors, String failure, String identity) {
        boolean ok() {
            return errors.isEmpty() && failure == null;
        }
    }

    int minIntervalMinutes() {
        return minIntervalMinutes;
    }

    /** Every supplier of the store, integrations and price lists together, by name. */
    List<SupplierView> views(Store store, String suppliersPath) {
        SupplierConnectionViewFactory.SupplierConnectionViews views = viewFactory.views(store);
        Set<String> incomplete = connectionService.incompleteConnections(store);
        return Stream.concat(views.external().stream(), views.manual().stream())
                .sorted(Comparator.comparing(SupplierConnectionView::label, String.CASE_INSENSITIVE_ORDER))
                .map(view -> SupplierView.of(view, incomplete.contains(view.identity()), suppliersPath))
                .toList();
    }

    /** Integration types a supplier can be connected through, by name; the same type may be connected several times. */
    List<String> types() {
        return supplierRegistry.getExternalSupplierNames().stream()
                .filter(name -> providerFactory.getDescriptor(name) != null)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    List<ProviderField> fieldsOf(String type) {
        SupplierProviderDescriptor descriptor = type == null ? null : providerFactory.getDescriptor(type);
        return descriptor == null ? null : descriptor.configurationFields();
    }

    StoreSupplierConnection connection(Store store, String identity) {
        FulfilmentConfiguration configuration = store.getFulfilmentConfiguration();
        if (configuration == null) {
            return null;
        }
        return configuration.getSupplierConnections().stream()
                .filter(connection -> connection.getSupplierName().equals(identity))
                .findFirst().orElse(null);
    }

    boolean known(StoreSupplierConnection connection) {
        return connection.getMode() == ConnectionMode.MANUAL || supplierRegistry.exists(connection.getSupplierName());
    }

    SupplierSettingsForm formOf(Store store, StoreSupplierConnection connection) {
        String identity = connection.getSupplierName();
        if (connection.getMode() == ConnectionMode.MANUAL) {
            return SupplierSettingsForm.of(connection, Map.of(), null);
        }
        return SupplierSettingsForm.of(connection, connectionService.storedSettings(store, identity),
                fieldsOf(SupplierIdentity.typeOf(identity)));
    }

    Set<String> storedSecretKeys(Store store, String identity) {
        return identity == null ? Set.of() : connectionService.storedSecretKeys(store, identity);
    }

    boolean hasFeed(Store store, String identity) {
        return identity != null && manualSupplierService.hasFeed(store.getStoreId(), identity);
    }

    SaveResult saveIntegration(Store store, StoreSupplierConnection existing, SupplierSettingsForm form, Locale locale) {
        String identity = existing == null ? null : existing.getSupplierName();
        String type = form.getProviderName();
        List<ProviderField> fields = fieldsOf(type);
        Map<String, String> errors = new LinkedHashMap<>();
        if (existing == null && (fields == null || !types().contains(type))) {
            errors.put("providerName", "store.suppliers.provider.required");
            return new SaveResult(errors, null, null);
        }
        errors.putAll(form.validate(fields, storedSecretKeys(store, identity), minIntervalMinutes));
        if (!errors.isEmpty()) {
            return new SaveResult(errors, null, null);
        }
        Map<String, String> configuration = form.global() ? Map.of() : form.toConfiguration(fields);
        StoreSupplierConnectionService.ConnectionUpdateResult result =
                connectionService.connectOrUpdate(store, form.toSelection(identity), configuration);
        if (result.hasErrors()) {
            return fromServiceErrors(result.errors(), locale);
        }
        return new SaveResult(Map.of(), null, result.identity());
    }

    SaveResult saveCsv(Store store, StoreSupplierConnection existing, SupplierSettingsForm form, Locale locale) {
        String identity = existing == null ? null : existing.getSupplierName();
        Map<String, String> errors = new LinkedHashMap<>(form.validate(null, Set.of(), minIntervalMinutes));
        if (!errors.containsKey("label")) {
            String problem = manualSupplierService.labelProblem(store, identity, form.getLabel());
            if (problem != null) {
                errors.put("label", SERVICE_ERRORS.get(problem).messageKey());
            }
        }
        byte[] file = null;
        if (form.hasFile()) {
            try {
                file = form.getFile().getBytes();
            } catch (IOException e) {
                file = new byte[0];
            }
            if (!manualSupplierService.isLoadable(file)) {
                errors.put("file", "store.suppliers.file.invalid");
            }
        }
        if (!errors.isEmpty()) {
            return new SaveResult(errors, null, null);
        }

        String storeId = store.getStoreId();
        boolean createdNow = identity == null;
        if (createdNow) {
            ManualSupplierService.Result created = manualSupplierService.create(storeId, form.getLabel());
            if (!created.ok()) {
                return fromServiceErrors(List.of(ErrorMessage.of(created.messageCode())), locale);
            }
            identity = created.identity();
        }
        ManualSupplierService.Result failed;
        try {
            failed = uploadAndApply(storeId, identity, file, form);
        } catch (RuntimeException e) {
            removeCreated(createdNow, storeId, identity);
            throw e;
        }
        if (failed != null) {
            removeCreated(createdNow, storeId, identity);
            return fromServiceErrors(List.of(ErrorMessage.of(failed.messageCode())), locale);
        }
        return new SaveResult(Map.of(), null, identity);
    }

    /** The first refused step after the price list exists, or null when the file and the settings are stored. */
    private ManualSupplierService.Result uploadAndApply(String storeId, String identity, byte[] file, SupplierSettingsForm form) {
        if (file != null) {
            ManualSupplierService.Result uploaded = manualSupplierService.uploadFeed(storeId, identity, file);
            if (!uploaded.ok()) {
                return uploaded;
            }
        }
        ManualSupplierService.Result applied = manualSupplierService.applySelections(storeId, List.of(
                new ManualSupplierService.ManualSelection(identity, form.isEnabled(), form.isIncludeInPricing(),
                        form.isIncludeInFulfilment(), form.getExternalSupplierId(), form.getLabel(), form.getBillingShortcut())));
        return applied.ok() ? null : applied;
    }

    /**
     * A price list created by this save but left without its file or settings is removed again: otherwise it stays as
     * an empty, switched-off entry and saving again is refused because the name is taken.
     */
    private void removeCreated(boolean createdNow, String storeId, String identity) {
        if (!createdNow) {
            return;
        }
        try {
            manualSupplierService.delete(storeId, identity);
        } catch (RuntimeException e) {
            log.error("Could not remove price list {} of store {} after a failed save", identity, storeId, e);
        }
    }

    /** Disconnects an integration or deletes a price list; false when nothing changed (the store was left as it was). */
    boolean remove(Store store, StoreSupplierConnection connection) {
        if (connection.getMode() == ConnectionMode.MANUAL) {
            return manualSupplierService.delete(store.getStoreId(), connection.getSupplierName()).ok();
        }
        return !connectionService.disconnect(store, connection.getSupplierName()).hasErrors();
    }

    /** The admin's settings for the store's suppliers; false when saving failed and nothing changed. */
    boolean saveAdminSettings(Store store, boolean canUseGlobalSuppliers, Integer inventoryCacheTtlMinutes) {
        FulfilmentConfiguration current = store.getFulfilmentConfiguration() != null
                ? store.getFulfilmentConfiguration() : new FulfilmentConfiguration();
        FulfilmentConfiguration submitted = current.withConnections(current.getSupplierConnections());
        submitted.setCanUseGlobalSuppliers(canUseGlobalSuppliers);
        submitted.setInventoryCacheTtlMinutes(inventoryCacheTtlMinutes);
        return !connectionService.applyStoreSettings(store, submitted, true).hasErrors();
    }

    private SaveResult fromServiceErrors(List<ErrorMessage> serviceErrors, Locale locale) {
        Map<String, String> errors = new LinkedHashMap<>();
        List<String> failures = new ArrayList<>();
        for (ErrorMessage error : serviceErrors) {
            FieldError field = SERVICE_ERRORS.get(error.code());
            if (field != null) {
                errors.putIfAbsent(field.field(), field.messageKey());
            } else {
                failures.add(messageSource.getMessage(error.code(), error.args(), locale));
            }
        }
        String failure = failures.isEmpty() ? null : failures.stream().collect(Collectors.joining(" "));
        return new SaveResult(errors, failure, null);
    }
}
