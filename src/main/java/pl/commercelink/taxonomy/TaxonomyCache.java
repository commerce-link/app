package pl.commercelink.taxonomy;

import com.nimbusds.oauth2.sdk.util.StringUtils;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.stereotype.Component;
import pl.commercelink.inventory.InventoryKey;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static pl.commercelink.taxonomy.UnifiedProductIdentifiers.unifyEan;

@Slf4j
@Component
public class TaxonomyCache {

    private final TaxonomyRepository taxonomyRepository;

    private String fileName = "N/A";

    private final ConcurrentHashMap<String, Taxonomy> taxonomyByEan = new ConcurrentHashMap<>();
    private final TaxonomyCodeIndex codeIndex = new TaxonomyCodeIndex();
    private final AtomicInteger pendingCount = new AtomicInteger();
    private final ConcurrentHashMap<String, String> stringPool = new ConcurrentHashMap<>();

    public TaxonomyCache(TaxonomyRepository taxonomyRepository) {
        this.taxonomyRepository = taxonomyRepository;
    }

    @PostConstruct
    void onStartUp() {
        Pair<String, List<Taxonomy>> result = taxonomyRepository.loadNewest();

        this.fileName = result.getLeft();
        taxonomyByEan.clear();
        codeIndex.clear();
        result.getRight().forEach(this::add);
        pendingCount.set((int) taxonomyByEan.values().stream().filter(taxonomy -> !hasCategory(taxonomy)).count());

        log.info("Loaded {} taxonomies by ean into cache from file: {}", taxonomyByEan.size(), fileName);
    }

    public void add(Taxonomy taxonomy) {
        String ean = unifyEan(taxonomy.ean());
        if (StringUtils.isBlank(ean)) return;
        taxonomyByEan.compute(ean, (key, current) -> {
            Taxonomy merged = current == null ? taxonomy : current.mergedWith(taxonomy);
            if (merged.equals(current)) {
                return current;
            }
            Taxonomy stored = internLowCardinalityFields(merged);
            pendingCount.addAndGet(pendingDelta(current, stored));
            return stored;
        });
        codeIndex.link(taxonomy.mfn(), taxonomy.brand(), ean);
    }

    private static int pendingDelta(Taxonomy current, Taxonomy merged) {
        int before = current != null && !hasCategory(current) ? 1 : 0;
        int after = hasCategory(merged) ? 0 : 1;
        return after - before;
    }

    public int pendingCount() {
        return pendingCount.get();
    }

    public static boolean hasCategory(Taxonomy taxonomy) {
        return taxonomy != null && taxonomy.hasCategory();
    }

    public boolean updateCategory(String ean, String category, String categoryId) {
        String key = unifyEan(ean);
        if (StringUtils.isBlank(key) || category == null || category.isBlank()
                || categoryId == null || categoryId.isBlank()) {
            return false;
        }
        boolean[] updated = {false};
        taxonomyByEan.computeIfPresent(key, (k, current) -> {
            if (hasCategory(current)) {
                return current;
            }
            updated[0] = true;
            pendingCount.decrementAndGet();
            return current.withCategory(pooled(category), pooled(categoryId));
        });
        return updated[0];
    }

    public Taxonomy find(InventoryKey inventoryKey) {
        Taxonomy taxonomy = Taxonomy.EMPTY;

        for (Taxonomy t : recordsOf(inventoryKey.getProductEans())) {
            if (t.isPreferredOver(taxonomy)) {
                taxonomy = t;
            }
        }

        return taxonomy;
    }

    public void blockCodeSource(String mfn, String brand, String ean) {
        String key = unifyEan(ean);
        if (StringUtils.isBlank(key)) return;
        codeIndex.block(mfn, brand, key);
    }

    public Taxonomy findCategorySource(Taxonomy row) {
        Taxonomy own = findByEan(row.ean());
        if (hasCategory(own)) {
            return own;
        }
        return best(recordsOf(codeIndex.eansOf(row.mfn(), row.brand())).stream()
                .filter(Taxonomy::hasCategory)
                .toList());
    }

    public Taxonomy findByEan(String ean) {
        String key = unifyEan(ean);
        if (StringUtils.isBlank(key)) {
            return null;
        }
        return taxonomyByEan.get(key);
    }

    private List<Taxonomy> recordsOf(Collection<String> eans) {
        return eans.stream()
                .filter(Objects::nonNull)
                .map(taxonomyByEan::get)
                .filter(Objects::nonNull)
                .toList();
    }

    private static Taxonomy best(List<Taxonomy> records) {
        Taxonomy best = null;
        for (Taxonomy t : records) {
            if (best == null || t.isPreferredOver(best)) {
                best = t;
            }
        }
        return best;
    }

    public String getFileName() {
        return fileName;
    }

    public Collection<Taxonomy> getTaxonomies() {
        return taxonomyByEan.values();
    }

    public int size() {
        return taxonomyByEan.size();
    }

    private String pooled(String value) {
        if (value == null) return null;
        String existing = stringPool.putIfAbsent(value, value);
        return existing != null ? existing : value;
    }

    private Taxonomy internLowCardinalityFields(Taxonomy taxonomy) {
        String brand = pooled(taxonomy.brand());
        String category = pooled(taxonomy.category());
        String categoryId = pooled(taxonomy.categoryId());
        if (brand == taxonomy.brand() && category == taxonomy.category() && categoryId == taxonomy.categoryId()) {
            return taxonomy;
        }
        return new Taxonomy(taxonomy.ean(), taxonomy.mfn(), brand, taxonomy.name(),
                category, taxonomy.dataAccuracyScore(),
                taxonomy.netWeightInGrams(), taxonomy.grossWeightInGrams(),
                taxonomy.rawCategory(), categoryId);
    }

}
