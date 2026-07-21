package pl.commercelink.taxonomy;

import org.springframework.stereotype.Component;
import pl.commercelink.inventory.supplier.api.Taxonomy;
import pl.commercelink.pim.api.CategoryMatchedEvent;

import java.util.concurrent.atomic.AtomicInteger;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

@Component
public class TaxonomyCategoryEnrichment {

    private final TaxonomyCache taxonomyCache;
    private final TaxonomyCategoryMatchProperties properties;
    private final AtomicInteger pendingCount = new AtomicInteger();

    TaxonomyCategoryEnrichment(TaxonomyCache taxonomyCache, TaxonomyCategoryMatchProperties properties) {
        this.taxonomyCache = taxonomyCache;
        this.properties = properties;
    }

    public Taxonomy enrich(Taxonomy taxonomy) {
        if (TaxonomyCache.hasCategory(taxonomy) || taxonomy.mfn() == null || taxonomy.mfn().isEmpty()) {
            return taxonomy;
        }
        Taxonomy cached = taxonomyCache.findByMfn(taxonomy.mfn());
        if (cached == null || !TaxonomyCache.hasCategory(cached)) {
            return taxonomy;
        }
        return new Taxonomy(taxonomy.ean(), taxonomy.mfn(), taxonomy.brand(), taxonomy.name(),
                cached.category(), taxonomy.dataAccuracyScore(),
                taxonomy.netWeightInGrams(), taxonomy.grossWeightInGrams());
    }

    public boolean isPendingEligible(String supplierName, Taxonomy taxonomy) {
        return properties.allows(supplierName)
                && isNotBlank(taxonomy.mfn()) && isNotBlank(taxonomy.ean())
                && isNotBlank(taxonomy.brand()) && isNotBlank(taxonomy.name())
                && pendingCount.get() < properties.pendingCap();
    }

    public void addPending(Taxonomy taxonomy) {
        if (taxonomyCache.findByMfn(taxonomy.mfn()) == null) {
            pendingCount.incrementAndGet();
        }
        taxonomyCache.add(taxonomy);
    }

    public void applyMatch(CategoryMatchedEvent event) {
        if (event == null || event.mfn() == null || event.category() == null
                || Taxonomy.OTHER.equals(event.category())) {
            return;
        }
        if (taxonomyCache.updateCategory(event.mfn(), event.category())) {
            pendingCount.decrementAndGet();
            System.out.println("Category match applied: mfn=" + event.mfn()
                    + " category=" + event.category() + " source=" + event.source());
        }
    }

    public int pendingCount() {
        return pendingCount.get();
    }
}
