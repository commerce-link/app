package pl.commercelink.taxonomy;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class TaxonomyCategoryMatchProperties {

    private final Set<String> suppliers;
    private final int buckets;
    private final int pendingCap;

    TaxonomyCategoryMatchProperties(
            @Value("${taxonomy.category-match.suppliers:}") String suppliers,
            @Value("${taxonomy.category-match.buckets:100}") int buckets,
            @Value("${taxonomy.category-match.pending-cap:300000}") int pendingCap) {
        if (buckets < 1) {
            throw new IllegalArgumentException("taxonomy.category-match.buckets must be at least 1, got: " + buckets);
        }
        this.suppliers = Arrays.stream(suppliers.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
        this.buckets = buckets;
        this.pendingCap = pendingCap;
    }

    public boolean enabled() {
        return !suppliers.isEmpty();
    }

    public boolean allows(String supplierName) {
        if (supplierName == null) {
            return false;
        }
        return suppliers.contains(supplierName)
                || (supplierName.startsWith("manual:") && suppliers.contains("Manual"));
    }

    public int buckets() {
        return buckets;
    }

    public int pendingCap() {
        return pendingCap;
    }
}
