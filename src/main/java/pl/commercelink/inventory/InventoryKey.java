package pl.commercelink.inventory;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import pl.commercelink.pim.api.PimEntry;
import pl.commercelink.products.Product;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import static pl.commercelink.taxonomy.UnifiedProductIdentifiers.*;

public class InventoryKey {

    // id not always present
    @JsonIgnore
    private String id;
    @JsonProperty("eans")
    private Set<String> eans = new HashSet<>();
    @JsonProperty("productCodes")
    private Set<String> productCodes = new HashSet<>();

    public InventoryKey() {
    }

    public InventoryKey(String id) {
        this.id = id;
    }

    public InventoryKey(String ean, String productCode) {
        if (ean != null) {
            this.eans.add(unifyEan(ean));
        }
        if (productCode != null) {
            this.productCodes.add(unifyMfn(productCode));
        }
    }

    public InventoryKey(Set<String> eans, Set<String> productCodes) {
        this.eans = unifyEans(eans);
        this.productCodes = unifyMfns(productCodes);
    }

    public void addEan(String ean) {
        this.eans.add(unifyEan(ean));
    }

    public void addManufacturerCode(String manufacturerCode) {
        this.productCodes.add(unifyMfn(manufacturerCode));
    }

    public boolean matches(InventoryKey other) {
        boolean matched = false;

        if (id != null && other.id != null) {
            matched = id.equals(other.id);
        }

        return matched || hasAnyEan(other.eans) || hasAnyProductCode(other.productCodes);
    }

    public boolean hasAnyEan(Collection<String> eans) {
        return eans.stream().anyMatch(this.eans::contains);
    }

    public boolean hasAnyProductCode(Collection<String> productCodes) {
        return productCodes.stream().anyMatch(this.productCodes::contains);
    }

    public String getId() {
        return id;
    }

    public Collection<String> getProductEans() {
        return unifyEans(eans);
    }

    public Collection<String> getProductCodes() {
        return unifyMfns(productCodes);
    }

    public static InventoryKey fromProduct(Product product) {
        return new InventoryKey(product.getEan(), product.getManufacturerCode());
    }

    public static InventoryKey fromEan(String ean) {
        return new InventoryKey(ean, null);
    }

    public static InventoryKey fromMfn(String manufacturerCode) {
        return new InventoryKey(null, manufacturerCode);
    }

    public static InventoryKey fromPimId(String pimId) {
        return new InventoryKey(pimId);
    }

    public static InventoryKey fromPimEntry(PimEntry entry) {
        InventoryKey key = new InventoryKey(entry.pimId());
        entry.gtins().forEach(key::addEan);
        entry.mpns().forEach(key::addManufacturerCode);
        return key;
    }

    public InventoryKey copy() {
        InventoryKey copy = new InventoryKey();
        copy.id = id;
        copy.eans = new HashSet<>(eans);
        copy.productCodes = new HashSet<>(productCodes);
        return copy;
    }

    @Override
    public String toString() {
        return "InventoryKey{" +
                "id=" + id +
                "eans=" + eans +
                ", productCodes=" + productCodes +
                '}';
    }

}
