package pl.commercelink.taxonomy;

import com.nimbusds.oauth2.sdk.util.StringUtils;
import jakarta.annotation.PostConstruct;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.stereotype.Component;
import pl.commercelink.inventory.InventoryKey;
import pl.commercelink.inventory.supplier.api.Taxonomy;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Stream;

@Component
public class TaxonomyCache {

    private final TaxonomyRepository taxonomyRepository;

    private String fileName = "N/A";
    private ConcurrentHashMap<String, Taxonomy> taxonomyByMfn = new ConcurrentHashMap<>();
    private final AtomicInteger pendingCount = new AtomicInteger();

    public TaxonomyCache(TaxonomyRepository taxonomyRepository) {
        this.taxonomyRepository = taxonomyRepository;
    }

    @PostConstruct
    void onStartUp() {
        Pair<String, List<Taxonomy>> result = taxonomyRepository.loadNewest();

        this.fileName = result.getLeft();
        result.getRight().forEach(cachedTaxonomy -> {
            taxonomyByMfn.put(cachedTaxonomy.mfn(), cachedTaxonomy);
        });
        pendingCount.set((int) taxonomyByMfn.values().stream().filter(taxonomy -> !hasCategory(taxonomy)).count());

        System.out.println("Loaded " + taxonomyByMfn.size() + " taxonomies by mfn into cache from file: " + fileName);
    }

    public void add(Taxonomy taxonomy) {
        if (StringUtils.isBlank(taxonomy.mfn())) return;
        taxonomyByMfn.compute(taxonomy.mfn(), (mfn, current) -> {
            Taxonomy merged = mergeOf(current, taxonomy);
            pendingCount.addAndGet(pendingDelta(current, merged));
            return merged;
        });
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
        return taxonomy != null
                && taxonomy.category() != null
                && !taxonomy.category().isBlank()
                && !Taxonomy.OTHER.equals(taxonomy.category());
    }

    public boolean updateCategory(String mfn, String category) {
        if (StringUtils.isBlank(mfn) || category == null || Taxonomy.OTHER.equals(category)) {
            return false;
        }
        Optional<String> known = ProductCategories.tryParse(category);
        if (known.isEmpty()) {
            System.out.println("Ignoring category match with unknown category key: " + category);
            return false;
        }
        boolean[] updated = {false};
        taxonomyByMfn.computeIfPresent(mfn, (key, current) -> {
            if (hasCategory(current)) {
                return current;
            }
            updated[0] = true;
            pendingCount.decrementAndGet();
            return new Taxonomy(current.ean(), current.mfn(), current.brand(), current.name(),
                    known.get(), current.dataAccuracyScore(),
                    current.netWeightInGrams(), current.grossWeightInGrams());
        });
        return updated[0];
    }

    private static Taxonomy mergeOf(Taxonomy current, Taxonomy incoming) {
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
                            t.category(), t.dataAccuracyScore(), net, gross, t.rawCategory());
    }

    public Taxonomy find(InventoryKey inventoryKey) {
        Taxonomy taxonomy = Taxonomy.EMPTY;

        for (String productCode : inventoryKey.getProductCodes()) {
            Taxonomy t = taxonomyByMfn.get(productCode);
            if (t != null && preferredOver(t, taxonomy)) {
                taxonomy = t;
            }
        }

        return taxonomy;
    }

    private static boolean preferredOver(Taxonomy candidate, Taxonomy current) {
        if (hasCategory(candidate) != hasCategory(current)) {
            return hasCategory(candidate);
        }
        return candidate.dataAccuracyScore() < current.dataAccuracyScore();
    }

    public Taxonomy findByMfn(String mfn) {
        return taxonomyByMfn.get(mfn);
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

}
