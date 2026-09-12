package pl.commercelink.inventory.supplier;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import pl.commercelink.provider.api.ProviderField;
import pl.commercelink.scheduling.InvalidScheduleException;
import pl.commercelink.scheduling.PollingSchedule;
import pl.commercelink.stores.ConnectionMode;
import pl.commercelink.stores.StoreSupplierConnection;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.apache.commons.lang3.StringUtils.isBlank;

@Component
public class SupplierConnectionValidator {

    private final int minIntervalMinutes;

    public SupplierConnectionValidator(@Value("${scheduling.min-interval-minutes}") int minIntervalMinutes) {
        this.minIntervalMinutes = minIntervalMinutes;
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
                List<ProviderField> fields = supplierFields.getOrDefault(name, List.of());
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
