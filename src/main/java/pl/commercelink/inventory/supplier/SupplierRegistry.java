package pl.commercelink.inventory.supplier;

import org.springframework.stereotype.Component;
import pl.commercelink.starter.secrets.SecretsManager;
import pl.commercelink.inventory.supplier.api.*;
import pl.commercelink.inventory.supplier.api.support.ResourceDownloadException;

import java.util.*;

@Component
public class SupplierRegistry {

    public static final String WAREHOUSE = "Warehouse";
    public static final String OTHER = "Other";

    private static final SupplierInfo OTHER_ENTITY = new SupplierInfo("Other", SupplierType.Retailer, Integer.MAX_VALUE, "XX",
            new ShippingPolicy(new ShippingTerms(3, new ShippingCostPolicy.FlatRate(1000000, 18))));

    private final Map<String, SupplierDescriptor> descriptors = new LinkedHashMap<>();
    private final Map<String, SupplierInfo> suppliers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    private final SecretsManager secretsManager;

    public SupplierRegistry(SecretsManager secretsManager) {
        this.secretsManager = secretsManager;
        for (SupplierDescriptor descriptor : ServiceLoader.load(SupplierDescriptor.class)) {
            descriptors.put(descriptor.supplierInfo().name(), descriptor);
            suppliers.put(descriptor.supplierInfo().name(), descriptor.supplierInfo());
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

    public Optional<FeedData> downloadFeed(String supplierName) throws ResourceDownloadException {
        SupplierDescriptor descriptor = descriptors.get(supplierName);
        if (descriptor == null) {
            return Optional.empty();
        }
        String secret = secretsManager.getSecret(supplierName);
        return descriptor.download(secret);
    }

    public Collection<SupplierDescriptor> getAllDescriptors() {
        return descriptors.values();
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
