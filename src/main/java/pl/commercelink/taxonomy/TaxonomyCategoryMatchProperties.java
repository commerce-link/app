package pl.commercelink.taxonomy;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class TaxonomyCategoryMatchProperties {

    private final int buckets;
    private final int pendingCap;
    private final int mappingMinSamples;
    private final double mappingMinShare;
    private final double mappingMinConfidence;
    private final int mappingTrickleEvery;

    TaxonomyCategoryMatchProperties(int buckets, int pendingCap) {
        this(buckets, pendingCap, 5, 0.9, 0.9, 20);
    }

    @Autowired
    TaxonomyCategoryMatchProperties(
            @Value("${taxonomy.category-match.buckets:100}") int buckets,
            @Value("${taxonomy.category-match.pending-cap:300000}") int pendingCap,
            @Value("${taxonomy.category-match.mapping.min-samples:5}") int mappingMinSamples,
            @Value("${taxonomy.category-match.mapping.min-share:0.9}") double mappingMinShare,
            @Value("${taxonomy.category-match.mapping.min-confidence:0.9}") double mappingMinConfidence,
            @Value("${taxonomy.category-match.mapping.trickle-every:20}") int mappingTrickleEvery) {
        if (buckets < 1) {
            throw new IllegalArgumentException("taxonomy.category-match.buckets must be at least 1, got: " + buckets);
        }
        if (mappingMinSamples < 1) {
            throw new IllegalArgumentException("taxonomy.category-match.mapping.min-samples must be at least 1, got: " + mappingMinSamples);
        }
        if (mappingMinShare <= 0 || mappingMinShare > 1) {
            throw new IllegalArgumentException("taxonomy.category-match.mapping.min-share must be in (0,1], got: " + mappingMinShare);
        }
        if (mappingMinConfidence < 0 || mappingMinConfidence > 1) {
            throw new IllegalArgumentException("taxonomy.category-match.mapping.min-confidence must be in [0,1], got: " + mappingMinConfidence);
        }
        if (mappingTrickleEvery < 1) {
            throw new IllegalArgumentException("taxonomy.category-match.mapping.trickle-every must be at least 1, got: " + mappingTrickleEvery);
        }
        this.buckets = buckets;
        this.pendingCap = pendingCap;
        this.mappingMinSamples = mappingMinSamples;
        this.mappingMinShare = mappingMinShare;
        this.mappingMinConfidence = mappingMinConfidence;
        this.mappingTrickleEvery = mappingTrickleEvery;
    }

    public int buckets() {
        return buckets;
    }

    public int pendingCap() {
        return pendingCap;
    }

    public int mappingMinSamples() {
        return mappingMinSamples;
    }

    public double mappingMinShare() {
        return mappingMinShare;
    }

    public double mappingMinConfidence() {
        return mappingMinConfidence;
    }

    public int mappingTrickleEvery() {
        return mappingTrickleEvery;
    }
}
