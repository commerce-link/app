package pl.commercelink.inventory.supplier;

import org.springframework.stereotype.Component;
import pl.commercelink.inventory.supplier.api.*;
import pl.commercelink.inventory.supplier.manual.ManualSupplierInfos;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * SupplierProvider catalog: {@link SupplierInfo} metadata for every discovered supplier plus the
 * built-in non-plugin entities (Warehouse, Other). The list of supplier providers
 * lives in {@link SupplierProviderFactory}; this registry only owns catalog metadata.
 */
@Component
public class SupplierRegistry {

    public static final String WAREHOUSE = "Warehouse";
    public static final String OTHER = "Other";

    private static final SupplierInfo OTHER_ENTITY = new SupplierInfo("Other", SupplierType.Retailer, Integer.MAX_VALUE, "XX",
            new ShippingPolicy(new ShippingTerms(3, new ShippingCostPolicy.FlatRate(1000000, 18))));

    private final Map<String, SupplierInfo> suppliers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

    public SupplierRegistry(SupplierProviderFactory supplierProviderFactory) {
        for (SupplierProviderDescriptor descriptor : supplierProviderFactory.availableProviders()) {
            SupplierInfo info = descriptor.supplierInfo();
            if (!SupplierIdentity.isValidTypeName(info.name())) {
                // The identity separator would make `Type-token` ambiguous; fail at boot, not on first order.
                throw new IllegalStateException("Supplier type name must not contain '-' or ':': " + info.name());
            }
            register(info);
        }
        register(new SupplierInfo("Warehouse", SupplierType.Distributor, 1, "PL",
                new ShippingPolicy(new ShippingTerms(1, new ShippingCostPolicy.Free()))));
        register(OTHER_ENTITY);
    }

    private void register(SupplierInfo entity) {
        suppliers.put(entity.name(), entity);
    }

    public SupplierInfo get(String supplierName) {
        String type = SupplierIdentity.typeOf(supplierName);
        SupplierInfo info = type == null ? null : suppliers.get(type);
        if (info == null) {
            return defaultFor(supplierName);
        }
        return info.withName(supplierName);
    }

    private SupplierInfo defaultFor(String supplierName) {
        return SupplierIdentity.isManual(supplierName)
                ? ManualSupplierInfos.forIdentity(supplierName)
                : OTHER_ENTITY;
    }

    public boolean exists(String supplierName) {
        return supplierName != null && suppliers.containsKey(SupplierIdentity.typeOf(supplierName));
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
