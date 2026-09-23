package pl.commercelink.taxonomy;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import pl.commercelink.pim.api.CategoryMatchedEvent;
import pl.commercelink.taxonomy.mapping.CategoryMappingCache;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

@Component
@Slf4j
public class TaxonomyCategoryEnrichment {

    private static final int UNKNOWN = -1;

    private final TaxonomyCatalog catalog;
    private final PendingCategorizationRepository pendingRepository;
    private final TaxonomyCategoryMatchProperties properties;
    private final CategoryMappingCache mappingCache;
    private final AtomicInteger pendingSize = new AtomicInteger(UNKNOWN);

    TaxonomyCategoryEnrichment(TaxonomyCatalog catalog, PendingCategorizationRepository pendingRepository,
                               TaxonomyCategoryMatchProperties properties, CategoryMappingCache mappingCache) {
        this.catalog = catalog;
        this.pendingRepository = pendingRepository;
        this.properties = properties;
        this.mappingCache = mappingCache;
    }

    public Taxonomy enrich(Taxonomy taxonomy, Taxonomy stored) {
        if (Taxonomy.hasCategory(taxonomy) || isBlank(taxonomy.mfn()) || !Taxonomy.hasCategory(stored)) {
            return taxonomy;
        }
        return new Taxonomy(taxonomy.ean(), taxonomy.mfn(), taxonomy.brand(), taxonomy.name(),
                stored.category(), taxonomy.dataAccuracyScore(),
                taxonomy.netWeightInGrams(), taxonomy.grossWeightInGrams(),
                taxonomy.rawCategory(), stored.categoryId());
    }

    public boolean isPendingEligible(Taxonomy taxonomy) {
        return hasIdentificationData(taxonomy) && pendingCount() < properties.pendingCap();
    }

    public boolean hasIdentificationData(Taxonomy taxonomy) {
        return isNotBlank(taxonomy.mfn()) && isNotBlank(taxonomy.ean())
                && isNotBlank(taxonomy.brand()) && isNotBlank(taxonomy.name());
    }

    public void addPending(Taxonomy taxonomy, String supplier) {
        if (isBlank(taxonomy.mfn())) {
            return;
        }
        if (pendingRepository.add(taxonomy.mfn(), supplier, LocalDateTime.now())) {
            adjustPendingSize(1);
        }
    }

    public void applyMatch(CategoryMatchedEvent event) {
        if (event == null || event.mfn() == null || isBlank(event.category())) {
            return;
        }
        PendingCategorization pending = pendingRepository.find(event.mfn());
        if (!catalog.updateCategory(event.mfn(), event.category(), event.categoryId())) {
            return;
        }
        forget(event.mfn());
        log.info("Category match applied: mfn={} category={} source={}",
                event.mfn(), event.category(), event.source());
        learnMapping(event, pending);
    }

    void forget(String mfn) {
        pendingRepository.remove(mfn);
        adjustPendingSize(-1);
    }

    private void adjustPendingSize(int delta) {
        pendingCount();
        pendingSize.updateAndGet(size -> Math.max(0, size + delta));
    }

    private void learnMapping(CategoryMatchedEvent event, PendingCategorization pending) {
        String supplier = pending == null ? null : pending.getSupplier();
        if (isBlank(supplier) || isBlank(event.categoryId())) {
            return;
        }
        if (event.confidence() != null && event.confidence() < properties.mapping().minConfidence()) {
            return;
        }
        Taxonomy taxonomy = catalog.findByMfn(event.mfn());
        if (taxonomy == null || isBlank(taxonomy.rawCategory())) {
            return;
        }
        mappingCache.recordSample(supplier, taxonomy.rawCategory(), event.categoryId(), event.category());
    }

    public int pendingCount() {
        int known = pendingSize.get();
        if (known != UNKNOWN) {
            return known;
        }
        pendingSize.compareAndSet(UNKNOWN, pendingRepository.count());
        return pendingSize.get();
    }

    void pendingCountIs(int exact) {
        pendingSize.set(exact);
    }
}
