package pl.commercelink.inventory.supplier;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import pl.commercelink.inventory.supplier.api.*;
import pl.commercelink.inventory.supplier.api.support.ResourceDownloadException;
import pl.commercelink.provider.api.ProviderField;
import pl.commercelink.starter.secrets.SecretsManager;

import java.util.*;

@Component
public class SupplierRegistry {

    public static final String WAREHOUSE = "Warehouse";
    public static final String OTHER = "Other";

    private static final SupplierInfo OTHER_ENTITY = new SupplierInfo("Other", SupplierType.Retailer, Integer.MAX_VALUE, "XX",
            new ShippingPolicy(new ShippingTerms(3, new ShippingCostPolicy.FlatRate(1000000, 18))));

    private final SupplierProviderFactory supplierProviderFactory;
    private final SecretsManager secretsManager;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final Map<String, SupplierInfo> suppliers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

    public SupplierRegistry(SupplierProviderFactory supplierProviderFactory, SecretsManager secretsManager) {
        this.supplierProviderFactory = supplierProviderFactory;
        this.secretsManager = secretsManager;
        for (SupplierDescriptor descriptor : supplierProviderFactory.availableProviders()) {
            register(descriptor.supplierInfo());
        }
        register(new SupplierInfo("Amazon", SupplierType.Retailer, 1, "PL",
                new ShippingPolicy(new ShippingTerms(2, new ShippingCostPolicy.Free())),
                "https://www.amazon.pl/gp/your-account/order-details?ie=UTF8&orderID=%s"));
        register(new SupplierInfo("Warehouse", SupplierType.Distributor, 1, "PL",
                new ShippingPolicy(new ShippingTerms(1, new ShippingCostPolicy.Free()))));
        register(OTHER_ENTITY);
    }

    private void register(SupplierInfo entity) {
        suppliers.put(entity.name(), entity);
    }

    /** GLOBAL feed download (store-independent). Per-store downloads go through SupplierProviderFactory.get(store, name). */
    public Optional<FeedData> downloadFeed(String supplierName) throws ResourceDownloadException {
        SupplierDescriptor descriptor = supplierProviderFactory.getDescriptor(supplierName);
        if (descriptor == null) {
            return Optional.empty();
        }
        String secret = secretsManager.getSecret(supplierName);
        Map<String, String> config = decodeGlobalSecret(descriptor, secret);
        return descriptor.create(config).download();
    }

    private Map<String, String> decodeGlobalSecret(SupplierDescriptor descriptor, String secret) {
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

    public Optional<SupplierDescriptor> getDescriptor(String supplierName) {
        return Optional.ofNullable(supplierProviderFactory.getDescriptor(supplierName));
    }

    public Collection<SupplierDescriptor> getAllDescriptors() {
        return supplierProviderFactory.availableProviders();
    }

    public SupplierInfo get(String supplierName) {
        return suppliers.getOrDefault(supplierName, OTHER_ENTITY);
    }

    public boolean exists(String supplierName) {
        return suppliers.containsKey(supplierName);
    }

    public Collection<String> getAllSupplierNames() {
        return suppliers.keySet();
    }

    public List<String> getExternalSupplierNames() {
        return suppliers.keySet().stream()
                .filter(name -> !name.equalsIgnoreCase(WAREHOUSE) && !name.equalsIgnoreCase(OTHER))
                .toList();
    }

    public String getPartnerSiteUrl(String supplierName, String externalDeliveryId) {
        return get(supplierName).getPartnerSiteUrl(externalDeliveryId);
    }
}
