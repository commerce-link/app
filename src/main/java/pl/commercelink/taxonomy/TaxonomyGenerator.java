package pl.commercelink.taxonomy;

import io.awspring.cloud.sqs.annotation.SqsListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import pl.commercelink.inventory.supplier.api.Taxonomy;
import pl.commercelink.pim.api.PimCatalog;
import pl.commercelink.pim.api.PimCategory;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@ConditionalOnProperty(name = "application.env", havingValue = "prod", matchIfMissing = false)
public class TaxonomyGenerator {

    private static final String LANG = "pl";

    @Autowired
    private TaxonomyCache taxonomyCache;

    @Autowired
    private TaxonomyRepository taxonomyRepository;

    @Autowired
    private PimCatalog pimCatalog;

    @SqsListener(
            value = "supplier-taxonomy-queue",
            maxConcurrentMessages = "1",
            maxMessagesPerPoll = "1",
            pollTimeoutSeconds = "20"
    )
    public void handleMessage(String message) {
        Map<String, String> idToName = pimCatalog.allCategories().stream()
                .filter(category -> LANG.equals(category.lang()) && category.id() != null && category.name() != null)
                .collect(Collectors.toMap(PimCategory::id, PimCategory::name, (first, second) -> first));
        List<Taxonomy> categorized = taxonomyCache.getTaxonomies().stream()
                .filter(TaxonomyCache::hasCategory)
                .map(taxonomy -> refreshCategoryName(taxonomy, idToName))
                .toList();
        taxonomyRepository.save(categorized);
    }

    static Taxonomy refreshCategoryName(Taxonomy taxonomy, Map<String, String> idToName) {
        if (taxonomy.categoryId() == null) {
            return taxonomy;
        }
        String freshName = idToName.get(taxonomy.categoryId());
        if (freshName == null || freshName.equals(taxonomy.category())) {
            return taxonomy;
        }
        return new Taxonomy(taxonomy.ean(), taxonomy.mfn(), taxonomy.brand(), taxonomy.name(),
                freshName, taxonomy.dataAccuracyScore(),
                taxonomy.netWeightInGrams(), taxonomy.grossWeightInGrams(),
                taxonomy.rawCategory(), taxonomy.categoryId());
    }
}
