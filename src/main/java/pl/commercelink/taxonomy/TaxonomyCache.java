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
        Taxonomy t = taxonomyByMfn.get(taxonomy.mfn());
        if (t == null || taxonomy.dataAccuracyScore() <= t.dataAccuracyScore()) {
            if (StringUtils.isNotBlank(taxonomy.mfn())) {
                taxonomyByMfn.put(taxonomy.mfn(), taxonomy);
            }
        }
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
