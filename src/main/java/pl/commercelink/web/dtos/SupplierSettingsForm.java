package pl.commercelink.web.dtos;

import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import pl.commercelink.inventory.supplier.SupplierConnectionValidator;
import pl.commercelink.inventory.supplier.SupplierIdentity;
import pl.commercelink.inventory.supplier.SupplierLabels;
import pl.commercelink.provider.api.ProviderField;
import pl.commercelink.scheduling.InvalidScheduleException;
import pl.commercelink.scheduling.PollingSchedule;
import pl.commercelink.stores.ConnectionMode;
import pl.commercelink.stores.StoreSupplierConnection;
import pl.commercelink.stores.SupplierSelectionForm;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A supplier on its own page: an integration (providerName = its type) with its own or the global access details, or a
 * price list uploaded by hand (providerName = {@link #CSV}). Adapter settings are posted per type as settings[type.key]
 * ({@link IntegrationSettingsForm}), so the page showing every type's fields without JavaScript saves only the chosen
 * one's. Fields that only an own connection or a price list has (name, access details, schedule, marketplace and invoicing
 * ids) are ignored for a global connection, which the service stores under its type name alone.
 */
@Getter
@Setter
public class SupplierSettingsForm extends IntegrationSettingsForm {

    public static final String CSV = SupplierIdentity.MANUAL_TYPE;

    private String mode = ConnectionMode.OWN.name();
    private String label;
    private String feedSchedule;
    // False unless posted, like any checkbox; a new supplier's form starts from newSupplier() with them ticked.
    private boolean includeInPricing;
    private boolean includeInFulfilment;
    private boolean enabled;
    private String externalSupplierId;
    private String billingShortcut;
    private MultipartFile file;

    public static SupplierSettingsForm newSupplier(String providerName) {
        SupplierSettingsForm form = new SupplierSettingsForm();
        form.setProviderName(providerName);
        form.includeInPricing = true;
        form.includeInFulfilment = true;
        form.enabled = true;
        return form;
    }

    public static SupplierSettingsForm of(StoreSupplierConnection connection, Map<String, String> stored,
                                          List<ProviderField> fields) {
        String identity = connection.getSupplierName();
        boolean csv = connection.getMode() == ConnectionMode.MANUAL;
        String type = csv ? CSV : SupplierIdentity.typeOf(identity);
        IntegrationSettingsForm settings = IntegrationSettingsForm.from(type, stored, fields);
        SupplierSettingsForm form = new SupplierSettingsForm();
        form.setProviderName(type);
        form.setSettings(settings.getSettings());
        form.mode = csv ? ConnectionMode.OWN.name() : connection.getMode().name();
        form.label = SupplierLabels.labelOf(connection);
        form.feedSchedule = connection.getFeedSchedule();
        form.includeInPricing = connection.isIncludeInPricing();
        form.includeInFulfilment = connection.isIncludeInFulfilment();
        form.enabled = !csv || connection.isEnabled();
        form.externalSupplierId = connection.getExternalSupplierId();
        form.billingShortcut = connection.getBillingShortcut();
        return form;
    }

    public boolean csv() {
        return CSV.equals(getProviderName());
    }

    public boolean global() {
        return !csv() && ConnectionMode.GLOBAL.name().equals(mode);
    }

    public boolean hasFile() {
        return file != null && !file.isEmpty();
    }

    /**
     * Checks of the fields the page owns; the connection service still checks the rest (a name taken by another
     * connection, a type connected globally twice) and its errors are shown at the same fields.
     *
     * @param fields           adapter settings of the chosen type, null for a price list
     * @param storedSecretKeys secrets already saved for this connection, which may be left empty
     */
    public Map<String, String> validate(List<ProviderField> fields, Set<String> storedSecretKeys, int minIntervalMinutes) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (global()) {
            return errors;
        }
        String name = StringUtils.trimToNull(label);
        if (name == null) {
            errors.put("label", "store.suppliers.label.required");
        } else if (name.length() > SupplierConnectionValidator.MAX_LABEL_LENGTH) {
            errors.put("label", "store.suppliers.label.tooLong");
        }
        if (csv()) {
            return errors;
        }
        if (fields != null) {
            errors.putAll(super.validate(fields, storedSecretKeys));
        }
        String schedule = PollingSchedule.normalizeOrNull(feedSchedule);
        if (schedule != null) {
            try {
                PollingSchedule.parse(schedule, minIntervalMinutes);
            } catch (InvalidScheduleException e) {
                errors.put("feedSchedule", e.getReason() == InvalidScheduleException.Reason.TOO_FREQUENT
                        ? "store.suppliers.schedule.tooFrequent" : "store.suppliers.schedule.invalid");
            }
        }
        return errors;
    }

    /** What the connection service saves for an integration; identity is null when adding. */
    public SupplierSelectionForm toSelection(String identity) {
        SupplierSelectionForm selection = new SupplierSelectionForm(getProviderName(),
                global() ? ConnectionMode.GLOBAL : ConnectionMode.OWN, includeInPricing, includeInFulfilment, feedSchedule);
        selection.setIdentity(identity);
        selection.setLabel(label);
        selection.setExternalSupplierId(externalSupplierId);
        selection.setBillingShortcut(billingShortcut);
        return selection;
    }
}
