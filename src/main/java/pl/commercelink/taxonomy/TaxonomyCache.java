package pl.commercelink.taxonomy;

import com.nimbusds.oauth2.sdk.util.StringUtils;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Stream;

@Component
@Slf4j
public class TaxonomyCache {

    private final TaxonomyRepository taxonomyRepository;

    private String fileName = "N/A";
    private ConcurrentHashMap<String, Taxonomy> taxonomyByMfn = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> stringPool = new ConcurrentHashMap<>();

    public TaxonomyCache(TaxonomyRepository taxonomyRepository) {
        this.taxonomyRepository = taxonomyRepository;
    }

    @PostConstruct
    void onStartUp() {
        Pair<String, List<Taxonomy>> result = taxonomyRepository.loadNewest();

        this.fileName = result.getLeft();
        result.getRight().forEach(cachedTaxonomy -> {
            Taxonomy interned = internLowCardinalityFields(cachedTaxonomy);
            taxonomyByMfn.put(interned.mfn(), interned);
        });

        log.info("Loaded {} taxonomies by mfn into cache from file: {}", taxonomyByMfn.size(), fileName);
    }

    public static boolean hasCategory(Taxonomy taxonomy) {
        return Taxonomy.hasCategory(taxonomy);
    }

    public boolean updateCategory(String mfn, String category, String categoryId) {
        if (StringUtils.isBlank(mfn) || category == null || category.isBlank()
                || categoryId == null || categoryId.isBlank()) {
            return false;
        }
        boolean[] updated = {false};
        taxonomyByMfn.computeIfPresent(mfn, (key, current) -> {
            if (hasCategory(current)) {
                return current;
            }
            updated[0] = true;
            return new Taxonomy(current.ean(), current.mfn(), current.brand(), current.name(),
                    pooled(category), current.dataAccuracyScore(),
                    current.netWeightInGrams(), current.grossWeightInGrams(),
                    current.rawCategory(), pooled(categoryId));
        });
        return updated[0];
    }

    static Taxonomy mergeOf(Taxonomy current, Taxonomy incoming) {
        if (current == null) return incoming;

        Taxonomy winner = bestByCategoryThenScore(current, incoming);
        Integer net = bestWeightOf(current, incoming, Taxonomy::netWeightInGrams);
        Integer gross = bestWeightOf(current, incoming, Taxonomy::grossWeightInGrams);

        return needsRebuild(winner, net, gross) ? withWeights(winner, net, gross) : winner;
    }

    private static Taxonomy bestByCategoryThenScore(Taxonomy current, Taxonomy incoming) {
        if (hasCategory(current) != hasCategory(incoming)) {
            return hasCategory(current) ? current : incoming;
        }
        return bestByScore(current, incoming);
    }

    private static Taxonomy bestByScore(Taxonomy a, Taxonomy b) {
        return b.dataAccuracyScore() <= a.dataAccuracyScore() ? b : a;
    }

    private static Integer bestWeightOf(Taxonomy a, Taxonomy b, Function<Taxonomy, Integer> picker) {
        return Stream.of(b, a)
                .filter(t -> picker.apply(t) != null)
                .min(Comparator.comparingInt(Taxonomy::dataAccuracyScore))
                .map(picker)
                .orElse(null);
    }

    private static boolean needsRebuild(Taxonomy winner, Integer net, Integer gross) {
        return !Objects.equals(net, winner.netWeightInGrams())
                || !Objects.equals(gross, winner.grossWeightInGrams());
    }

    private static Taxonomy withWeights(Taxonomy t, Integer net, Integer gross) {
        return new Taxonomy(t.ean(), t.mfn(), t.brand(), t.name(),
                            t.category(), t.dataAccuracyScore(), net, gross, t.rawCategory(), t.categoryId());
    }

    public Taxonomy findByMfn(String mfn) {
        return StringUtils.isBlank(mfn) ? null : taxonomyByMfn.get(mfn);
    }

    public Map<String, Taxonomy> findByMfns(Collection<String> mfns) {
        Map<String, Taxonomy> found = new LinkedHashMap<>();
        for (String mfn : mfns) {
            Taxonomy taxonomy = findByMfn(mfn);
            if (taxonomy != null) {
                found.put(mfn, taxonomy);
            }
        }
        return found;
    }

    public TaxonomyMerge openMerge(Collection<String> mfns) {
        return new TaxonomyMerge(findByMfns(mfns));
    }

    public void commit(TaxonomyMerge merge) {
        for (Taxonomy merged : merge.changed()) {
            Taxonomy candidate = internLowCardinalityFields(merged);
            taxonomyByMfn.merge(candidate.mfn(), candidate, (current, incoming) -> mergeOf(current, incoming));
        }
    }

    public Taxonomy findBest(Collection<String> productCodes) {
        return Taxonomy.bestOf(productCodes, findByMfns(productCodes));
    }

    public String getFileName() {
        return fileName;
    }

    public Collection<Taxonomy> getTaxonomies() {
        return taxonomyByMfn.values();
    }

    public int size() {
        return taxonomyByMfn.size();
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
