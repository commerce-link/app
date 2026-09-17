package pl.commercelink.inventory.supplier;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import pl.commercelink.provider.api.ProviderField;
import pl.commercelink.scheduling.InvalidScheduleException;
import pl.commercelink.scheduling.PollingSchedule;
import pl.commercelink.stores.ConnectionMode;
import pl.commercelink.stores.StoreSupplierConnection;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.apache.commons.lang3.StringUtils.isBlank;

@Component
public class SupplierConnectionValidator {

    public static final int MAX_LABEL_LENGTH = 60;

    private final int minIntervalMinutes;
    private final SupplierRegistry supplierRegistry;

    public SupplierConnectionValidator(@Value("${scheduling.min-interval-minutes}") int minIntervalMinutes,
                                       SupplierRegistry supplierRegistry) {
        this.minIntervalMinutes = minIntervalMinutes;
        this.supplierRegistry = supplierRegistry;
    }

    public List<ErrorMessage> validate(boolean canUseGlobalSuppliers,
                                       List<StoreSupplierConnection> connections,
                                       Map<String, List<ProviderField>> supplierFields,
                                       Map<String, Map<String, String>> submittedConfig,
                                       Set<String> suppliersWithStoredConfig) {
        List<ErrorMessage> errors = new ArrayList<>();
        if (connections == null) {
            return errors;
        }
        for (StoreSupplierConnection connection : connections) {
            String name = connection.getSupplierName();
            if (!canUseGlobalSuppliers && connection.getMode() == ConnectionMode.GLOBAL) {
                errors.add(ErrorMessage.of("store.supplier.connection.error.requires.own.connection", name));
                continue;
            }
            if (connection.getMode() == ConnectionMode.OWN) {
                List<ProviderField> fields = supplierFields.getOrDefault(SupplierIdentity.typeOf(name), List.of());
                Map<String, String> config = submittedConfig.getOrDefault(name, Map.of());
                boolean hasStored = suppliersWithStoredConfig.contains(name);
                for (ProviderField field : fields) {
                    if (!field.required()) {
                        continue;
                    }
                    boolean submitted = !isBlank(config.get(field.key()));
                    boolean preservedPassword = field.type() == ProviderField.FieldType.PASSWORD && hasStored;
                    if (!submitted && !preservedPassword) {
                        errors.add(ErrorMessage.of("store.supplier.connection.error.requires.field", name, field.label()));
                    }
                }
                validateSchedule(name, connection.getFeedSchedule(), errors);
            }
        }
        return errors;
    }

    public List<ErrorMessage> validateLabel(StoreSupplierConnection edited, Collection<String> otherLabels) {
        List<ErrorMessage> errors = new ArrayList<>();
        if (edited.getMode() == ConnectionMode.GLOBAL) {
            // A GLOBAL connection has no label of its own -- it is always shown under its type
            // name, so only a collision with another connection's label can go wrong here.
            String effective = SupplierLabels.labelOf(edited);
            if (taken(effective, otherLabels)) {
                errors.add(ErrorMessage.of("store.supplier.connection.error.label.taken", effective));
            }
            return errors;
        }
        String label = edited.getLabel();
        if (isBlank(label)) {
            errors.add(ErrorMessage.of("store.supplier.connection.error.label.required"));
        } else if (label.trim().length() > MAX_LABEL_LENGTH) {
            errors.add(ErrorMessage.of("store.supplier.connection.error.label.too.long"));
        } else if (isReservedFor(edited, label.trim())) {
            errors.add(ErrorMessage.of("store.supplier.connection.error.label.reserved"));
        } else if (taken(label.trim(), otherLabels)) {
            errors.add(ErrorMessage.of("store.supplier.connection.error.label.taken", label.trim()));
        }
        return errors;
    }

    private static boolean taken(String label, Collection<String> otherLabels) {
        return otherLabels.stream().anyMatch(other -> other != null && other.equalsIgnoreCase(label));
    }

    // Built-in registry entries (Warehouse, Other) and other adapters' type names would
    // make the label ambiguous in every supplier select. The connection's own type stays allowed:
    // legacy OWN connections (identity = type) already carry it and the modal suggests it for the
    // first tokened instance.
    private boolean isReservedFor(StoreSupplierConnection edited, String label) {
        String ownType = SupplierIdentity.typeOf(edited.getSupplierName());
        return supplierRegistry.getAllSupplierNames().stream()
                .filter(name -> !name.equalsIgnoreCase(ownType))
                .anyMatch(name -> name.equalsIgnoreCase(label));
    }

    private void validateSchedule(String supplierName, String feedSchedule, List<ErrorMessage> errors) {
        if (isBlank(feedSchedule)) {
            return;
        }
        try {
            PollingSchedule.parse(feedSchedule, minIntervalMinutes);
        } catch (InvalidScheduleException e) {
            if (e.getReason() == InvalidScheduleException.Reason.TOO_FREQUENT) {
                errors.add(ErrorMessage.of("store.supplier.connection.error.schedule.too.frequent", supplierName, minIntervalMinutes));
            } else {
                errors.add(ErrorMessage.of("store.supplier.connection.error.invalid.schedule", supplierName, feedSchedule));
            }
        }
    }
}
