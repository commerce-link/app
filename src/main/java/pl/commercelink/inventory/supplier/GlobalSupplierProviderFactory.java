package pl.commercelink.inventory.supplier;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.commercelink.inventory.supplier.api.SupplierProvider;
import pl.commercelink.inventory.supplier.api.SupplierProviderDescriptor;
import pl.commercelink.provider.api.AuthConfig;
import pl.commercelink.provider.api.ProviderField;
import pl.commercelink.starter.secrets.SecretsManager;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class GlobalSupplierProviderFactory {

    private final SupplierProviderFactory supplierProviderFactory;
    private final SecretsManager secretsManager;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public Optional<SupplierProvider> get(String supplierName) {
        SupplierProviderDescriptor descriptor = supplierProviderFactory.getDescriptor(supplierName);
        if (descriptor == null || descriptor.authConfig() instanceof AuthConfig.OAuth2) {
            return Optional.empty();
        }
        Map<String, String> config = decodeGlobalSecret(descriptor, secretsManager.getSecret(supplierName));
        return Optional.of(descriptor.create(config));
    }

    private Map<String, String> decodeGlobalSecret(SupplierProviderDescriptor descriptor, String secret) {
        if (secret == null || secret.isBlank()) {
            return Map.of();
        }
        if (secret.trim().startsWith("{")) {
            try {
                return objectMapper.readValue(secret, new TypeReference<Map<String, String>>() {});
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Failed to decode global supplier secret for " + descriptor.name(), e);
            }
        }
        List<ProviderField> fields = descriptor.configurationFields();
        String key = fields.isEmpty() ? "url" : fields.get(0).key();
        return Map.of(key, secret);
    }
}
