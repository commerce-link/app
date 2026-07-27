package pl.commercelink.taxonomy;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class TaxonomyCategoryMatchProperties {

    private final boolean enabled;
    private final int buckets;
    private final int pendingCap;

    TaxonomyCategoryMatchProperties(
            @Value("${taxonomy.category-match.enabled:true}") boolean enabled,
            @Value("${taxonomy.category-match.buckets:100}") int buckets,
            @Value("${taxonomy.category-match.pending-cap:300000}") int pendingCap) {
        if (buckets < 1) {
            throw new IllegalArgumentException("taxonomy.category-match.buckets must be at least 1, got: " + buckets);
        }
        this.enabled = enabled;
        this.buckets = buckets;
        this.pendingCap = pendingCap;
    }

    public boolean enabled() {
        return enabled;
    }

    public int buckets() {
        return buckets;
    }

    public int pendingCap() {
        return pendingCap;
    }
}
