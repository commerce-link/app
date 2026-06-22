package pl.commercelink.inventory.supplier;

import org.springframework.stereotype.Component;
import pl.commercelink.inventory.supplier.api.SupplierConfigField;
import pl.commercelink.stores.ConnectionMode;
import pl.commercelink.stores.StoreSupplierConnection;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.apache.commons.lang3.StringUtils.isBlank;

@Component
public class SupplierConnectionValidator {

    public List<ErrorMessage> validate(boolean canUseGlobalSuppliers,
                                       List<StoreSupplierConnection> connections,
                                       Map<String, List<SupplierConfigField>> supplierFields,
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
                List<SupplierConfigField> fields = supplierFields.getOrDefault(name, List.of());
                Map<String, String> config = submittedConfig.getOrDefault(name, Map.of());
                boolean hasStored = suppliersWithStoredConfig.contains(name);
                for (SupplierConfigField field : fields) {
                    if (!field.required()) {
                        continue;
                    }
                    boolean submitted = !isBlank(config.get(field.key()));
                    boolean preservedPassword = field.type() == SupplierConfigField.FieldType.PASSWORD && hasStored;
                    if (!submitted && !preservedPassword) {
                        errors.add(ErrorMessage.of("store.supplier.connection.error.requires.field", name, field.label()));
                    }
                }
            }
        }
        return errors;
    }
}
