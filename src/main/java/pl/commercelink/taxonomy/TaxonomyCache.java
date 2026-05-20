package pl.commercelink.taxonomy;

import com.nimbusds.oauth2.sdk.util.StringUtils;
import jakarta.annotation.PostConstruct;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.stereotype.Component;
import pl.commercelink.inventory.InventoryKey;
import pl.commercelink.inventory.supplier.api.Taxonomy;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

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

        Taxonomy recordWinner = incoming.dataAccuracyScore() <= current.dataAccuracyScore()
                ? incoming : current;

        Integer weight = bestWeight(current, incoming);

        if (weight == null ? recordWinner.weightInGrams() == null
                           : weight.equals(recordWinner.weightInGrams())) {
            return recordWinner;
        }
        return withWeight(recordWinner, weight);
    }

    private static Integer bestWeight(Taxonomy a, Taxonomy b) {
        if (a.weightInGrams() == null) return b.weightInGrams();
        if (b.weightInGrams() == null) return a.weightInGrams();
        return a.dataAccuracyScore() <= b.dataAccuracyScore() ? a.weightInGrams() : b.weightInGrams();
    }

    private static Taxonomy withWeight(Taxonomy t, Integer weight) {
        return new Taxonomy(t.ean(), t.mfn(), t.brand(), t.name(),
                            t.category(), t.dataAccuracyScore(), weight);
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
