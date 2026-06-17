package pl.commercelink.inventory.supplier;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.commercelink.inventory.supplier.api.SupplierConfigField;
import pl.commercelink.starter.secrets.SecretsManager;
import pl.commercelink.stores.Store;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.apache.commons.lang3.StringUtils.isBlank;

@Service
@RequiredArgsConstructor
public class SupplierConfigurationManager {

    private final Map<String, Map<String, String>> cache = new ConcurrentHashMap<>();
    private final SecretsManager secretsManager;

    public Map<String, String> getConfigurationForUI(Store store, String supplierName, List<SupplierConfigField> fields) {
        Map<String, String> masked = new HashMap<>(loadConfiguration(store, supplierName));
        for (SupplierConfigField field : fields) {
            if (field.type() == SupplierConfigField.FieldType.PASSWORD && masked.containsKey(field.key())) {
                masked.put(field.key(), "");
            }
        }
        return masked;
    }

    public void saveConfiguration(Store store, String supplierName, List<SupplierConfigField> fields, Map<String, String> configuration) {
        String secretName = store.getSecretesName(supplierName);
        Map<String, String> merged = new HashMap<>(configuration);

        if (secretsManager.exists(secretName)) {
            @SuppressWarnings("unchecked")
            Map<String, String> current = secretsManager.getSecret(secretName, Map.class);
            for (SupplierConfigField field : fields) {
                if (field.type() == SupplierConfigField.FieldType.PASSWORD && isBlank(merged.get(field.key()))) {
                    merged.put(field.key(), current.get(field.key()));
                }
            }
            secretsManager.updateSecret(secretName, merged);
            cache.remove(secretName);
        } else {
            boolean hasAllRequired = fields.stream()
                    .filter(SupplierConfigField::required)
                    .noneMatch(f -> isBlank(merged.get(f.key())));
            if (hasAllRequired) {
                secretsManager.createSecret(secretName, merged);
            }
        }
    }

    public void deleteConfiguration(Store store, String supplierName) {
        String secretName = store.getSecretesName(supplierName);
        if (secretsManager.exists(secretName)) {
            secretsManager.deleteSecret(secretName);
        }
        cache.remove(secretName);
    }

    public SecretSnapshot snapshot(Store store, String supplierName) {
        String secretName = store.getSecretesName(supplierName);
        if (secretsManager.exists(secretName)) {
            @SuppressWarnings("unchecked")
            Map<String, String> value = secretsManager.getSecret(secretName, Map.class);
            return new SecretSnapshot(true, value);
        }
        return new SecretSnapshot(false, null);
    }

    public void restore(Store store, String supplierName, SecretSnapshot snapshot) {
        String secretName = store.getSecretesName(supplierName);
        if (snapshot.existed()) {
            if (secretsManager.exists(secretName)) {
                secretsManager.updateSecret(secretName, snapshot.value());
            } else {
                secretsManager.createSecret(secretName, snapshot.value());
            }
        } else if (secretsManager.exists(secretName)) {
            secretsManager.deleteSecret(secretName);
        }
        cache.remove(secretName);
    }

    public Map<String, String> loadConfiguration(Store store, String supplierName) {
        String secretName = store.getSecretesName(supplierName);
        Map<String, String> cached = cache.get(secretName);
        if (cached != null) {
            return cached;
        }
        if (secretsManager.exists(secretName)) {
            @SuppressWarnings("unchecked")
            Map<String, String> config = secretsManager.getSecret(secretName, Map.class);
            cache.put(secretName, config);
            return config;
        }
        return new HashMap<>();
    }

    public record SecretSnapshot(boolean existed, Map<String, String> value) {
    }
}
