package pl.commercelink.products;

import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import pl.commercelink.inventory.InventoryKey;
import pl.commercelink.products.information.PimEntryRequester;
import pl.commercelink.products.information.StorePimAliases;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Stores products in a manual category, leaving out what the category already holds: a product its key matches (the
 * same pim id or a shared EAN), or one on the candidate's PIM entry, its clone or its source. The price list keys a
 * category's products by pim id, so one PIM entry is one product of the category. What a call stores guards the
 * candidates after it in the same way, and the PIM is asked at once for the entries of the stored products it may not
 * know yet ({@link PimEntryRequester}).
 */
@Component
@RequiredArgsConstructor
public class ManagedCategoryProducts {

    /** A product to store and the key it is recognised by among the category's products. */
    public record Candidate(Product product, InventoryKey key) {
    }

    /** @param alreadyInCategory the products the category held before the call, every one of them left as it was */
    public record Added(int added, int alreadyInCategory) {
    }

    private final ProductRepository productRepository;
    private final CatalogPlacement catalogPlacement;
    private final StorePimAliases pimAliases;
    private final PimEntryRequester pimEntryRequester;

    public Added addMissing(String storeId, String categoryId, List<Candidate> candidates) {
        return addMissing(storeId, categoryId, candidates, product -> {
        });
    }

    /**
     * @param complete what is worth resolving only for a product about to be stored (the PIM entry of a reviewed row):
     *                 applied to a candidate the category does not hold by its key, before its pim id is compared
     */
    public Added addMissing(String storeId, String categoryId, List<Candidate> candidates, Consumer<Product> complete) {
        List<Product> inCategory = productRepository.findAll(categoryId);
        List<InventoryKey> keysInCategory = new ArrayList<>(inCategory.stream()
                .map(InventoryKey::fromProduct)
                .toList());
        Set<String> pimIdsInCategory = inCategory.stream()
                .map(Product::getPimId)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toCollection(HashSet::new));
        List<Product> stored = new ArrayList<>();
        for (Candidate candidate : candidates) {
            if (keysInCategory.stream().anyMatch(candidate.key()::matches)) {
                continue;
            }
            complete.accept(candidate.product());
            String pimId = candidate.product().getPimId();
            if (holdsAny(pimIdsInCategory, storeId, pimId)) {
                continue;
            }
            try {
                productRepository.save(candidate.product());
                stored.add(candidate.product());
            } catch (ConditionalCheckFailedException e) {
                // A parallel request stored this very product first under the same id (the same review sent twice):
                // the category has it, exactly as if the read above had found it, so it guards the next candidates.
            }
            keysInCategory.add(candidate.key());
            if (StringUtils.isNotBlank(pimId)) {
                pimIdsInCategory.add(pimId);
            }
        }
        if (!stored.isEmpty()) {
            catalogPlacement.evict(storeId);
            // after all the writes, so the requests do not interleave with the saves; the requester decides which
            // products need one
            pimEntryRequester.requestMissingEntries(storeId, stored);
        }
        return new Added(stored.size(), inCategory.size());
    }

    private boolean holdsAny(Set<String> pimIds, String storeId, String pimId) {
        return pimAliases.aliasesOf(storeId, pimId).stream().anyMatch(pimIds::contains);
    }
}
