package pl.commercelink.provider;

import org.springframework.stereotype.Service;
import pl.commercelink.provider.api.ProviderDescriptor;
import pl.commercelink.provider.api.ProviderField;
import pl.commercelink.starter.secrets.SecretsManager;
import pl.commercelink.stores.Store;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.apache.commons.lang3.StringUtils.isBlank;

@Service
public class ProviderConfigurationManager {

    private final Map<String, Map<String, String>> cache = new ConcurrentHashMap<>();
    private final SecretsManager secretsManager;

    public ProviderConfigurationManager(SecretsManager secretsManager) {
        this.secretsManager = secretsManager;
    }

    public Map<String, String> getConfigurationForUI(Store store, ProviderDescriptor<?> descriptor) {
        return getConfigurationForUI(store, descriptor.name(), descriptor);
    }

    public Map<String, String> getConfigurationForUI(Store store, String configName, ProviderDescriptor<?> descriptor) {
        Map<String, String> config = loadConfiguration(store, configName);
        Map<String, String> masked = new HashMap<>(config);
        for (ProviderField field : descriptor.configurationFields()) {
            if (field.type() == ProviderField.FieldType.PASSWORD && masked.containsKey(field.key())) {
                masked.put(field.key(), "");
            }
        }
        return masked;
    }

    public void saveConfiguration(Store store, ProviderDescriptor<?> descriptor, Map<String, String> configuration) {
        saveConfiguration(store, descriptor.name(), descriptor, configuration);
    }

    public void saveConfiguration(Store store, String configName, ProviderDescriptor<?> descriptor, Map<String, String> configuration) {
        String secretName = store.getSecretesName(configName);

        Map<String, String> merged = new HashMap<>(configuration);
        if (secretsManager.exists(secretName)) {
            @SuppressWarnings("unchecked")
            Map<String, String> current = secretsManager.getSecret(secretName, Map.class);
            for (ProviderField field : descriptor.configurationFields()) {
                if (field.type() == ProviderField.FieldType.PASSWORD && isBlank(merged.get(field.key()))) {
                    merged.put(field.key(), current.get(field.key()));
                }
            }
            secretsManager.updateSecret(secretName, merged);
            cache.remove(secretName);
        } else {
            boolean hasAllRequired = descriptor.configurationFields().stream()
                    .filter(ProviderField::required)
                    .noneMatch(f -> isBlank(merged.get(f.key())));
            if (hasAllRequired) {
                secretsManager.createSecret(secretName, merged);
            }
        }
    }

    public void deleteConfiguration(Store store, String configName) {
        String secretName = store.getSecretesName(configName);
        if (secretsManager.exists(secretName)) {
            secretsManager.deleteSecret(secretName);
        }
        cache.remove(secretName);
    }

    public Map<String, String> loadConfiguration(Store store, String providerName) {
        String secretName = store.getSecretesName(providerName);
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
}
