package pl.commercelink.taxonomy;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties(prefix = "taxonomy.category-match")
public record TaxonomyCategoryMatchProperties(
        @DefaultValue("1000") int pendingCap,
        @DefaultValue("10") int maxSubmissionsPerRun,
        @DefaultValue Mapping mapping,
        @DefaultValue("4") int maxAttempts,
        @DefaultValue("7d") Duration retryExhaustedAfter,
        @DefaultValue("1h") Duration retryAfter) {

    @ConstructorBinding
    public TaxonomyCategoryMatchProperties {
        if (maxSubmissionsPerRun < 1) {
            throw new IllegalArgumentException(
                    "taxonomy.category-match.max-submissions-per-run must be at least 1, got: " + maxSubmissionsPerRun);
        }
        if (maxAttempts < 0) {
            throw new IllegalArgumentException("taxonomy.category-match.max-attempts must not be negative, got: " + maxAttempts);
        }
        if (retryExhaustedAfter == null || retryExhaustedAfter.isNegative() || retryExhaustedAfter.isZero()) {
            throw new IllegalArgumentException(
                    "taxonomy.category-match.retry-exhausted-after must be positive, got: " + retryExhaustedAfter);
        }
        if (retryAfter == null || retryAfter.isNegative()) {
            throw new IllegalArgumentException(
                    "taxonomy.category-match.retry-after must not be negative, got: " + retryAfter);
        }
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
