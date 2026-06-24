package pl.commercelink.inventory.supplier;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.commercelink.inventory.InventoryRepository;
import pl.commercelink.inventory.supplier.api.SupplierProviderDescriptor;
import pl.commercelink.inventory.supplier.api.support.ResourceDownloadException;
import pl.commercelink.provider.api.ProviderField;
import pl.commercelink.starter.secrets.SecretsManager;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class GlobalSupplierFeedService {

    private final SupplierProviderFactory supplierProviderFactory;
    private final SecretsManager secretsManager;
    private final InventoryRepository inventoryRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public void loadFeed(String supplierName) throws ResourceDownloadException {
        SupplierProviderDescriptor descriptor = supplierProviderFactory.getDescriptor(supplierName);
        if (descriptor == null) {
            return;
        }
        String secret = secretsManager.getSecret(supplierName);
        Map<String, String> config = decodeGlobalSecret(descriptor, secret);
        descriptor.create(config).download().ifPresent(feedData ->
                inventoryRepository.store(supplierName, feedData.data(), feedData.extension()));
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
