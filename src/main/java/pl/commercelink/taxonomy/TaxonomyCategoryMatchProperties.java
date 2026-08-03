package pl.commercelink.taxonomy;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "taxonomy.category-match")
public record TaxonomyCategoryMatchProperties(
        @DefaultValue("100") int buckets,
        @DefaultValue("300000") int pendingCap,
        @DefaultValue Mapping mapping,
        @DefaultValue("4") int maxAttempts) {

    @ConstructorBinding
    public TaxonomyCategoryMatchProperties {
        if (buckets < 1) {
            throw new IllegalArgumentException("taxonomy.category-match.buckets must be at least 1, got: " + buckets);
        }
        if (maxAttempts < 0) {
            throw new IllegalArgumentException("taxonomy.category-match.max-attempts must not be negative, got: " + maxAttempts);
        }
    }

    public TaxonomyCategoryMatchProperties(int buckets, int pendingCap) {
        this(buckets, pendingCap, new Mapping(5, 0.9, 0.9, 20), 4);
    }

    public record Mapping(
            @DefaultValue("5") int minSamples,
            @DefaultValue("0.9") double minShare,
            @DefaultValue("0.9") double minConfidence,
            @DefaultValue("20") int trickleEvery) {

        public Mapping {
            if (minSamples < 1) {
                throw new IllegalArgumentException("taxonomy.category-match.mapping.min-samples must be at least 1, got: " + minSamples);
            }
            if (minShare <= 0 || minShare > 1) {
                throw new IllegalArgumentException("taxonomy.category-match.mapping.min-share must be in (0,1], got: " + minShare);
            }
            if (minConfidence < 0 || minConfidence > 1) {
                throw new IllegalArgumentException("taxonomy.category-match.mapping.min-confidence must be in [0,1], got: " + minConfidence);
            }
            if (trickleEvery < 1) {
                throw new IllegalArgumentException("taxonomy.category-match.mapping.trickle-every must be at least 1, got: " + trickleEvery);
            }
        }
    }
}
