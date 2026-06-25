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
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Stream;

@Component
public class TaxonomyCache {

    private final TaxonomyRepository taxonomyRepository;

    private String fileName = "N/A";
    private ConcurrentHashMap<String, Taxonomy> taxonomyByMfn = new ConcurrentHashMap<>();

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

        System.out.println("Loaded " + taxonomyByMfn.size() + " taxonomies by mfn into cache from file: " + fileName);
    }

    public void add(Taxonomy taxonomy) {
        if (StringUtils.isBlank(taxonomy.mfn())) return;
        taxonomyByMfn.compute(taxonomy.mfn(), (mfn, current) -> mergeOf(current, taxonomy));
    }

    private static Taxonomy mergeOf(Taxonomy current, Taxonomy incoming) {
        if (current == null) return incoming;

        Taxonomy winner = bestByScore(current, incoming);
        Integer net = bestWeightOf(current, incoming, Taxonomy::netWeightInGrams);
        Integer gross = bestWeightOf(current, incoming, Taxonomy::grossWeightInGrams);

        return needsRebuild(winner, net, gross) ? withWeights(winner, net, gross) : winner;
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
                            t.category(), t.dataAccuracyScore(), net, gross, t.categoryKey(), t.signals());
    }

    public Taxonomy find(InventoryKey inventoryKey) {
        Taxonomy taxonomy = Taxonomy.EMPTY;

        for (String productCode : inventoryKey.getProductCodes()) {
            Taxonomy t = taxonomyByMfn.get(productCode);
            if (t != null && t.dataAccuracyScore() < taxonomy.dataAccuracyScore()) {
                taxonomy = t;
            }
        }

        return taxonomy;
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
