package pl.commercelink.web.inventory;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.commercelink.inventory.BrowseRow;
import pl.commercelink.inventory.Inventory;
import pl.commercelink.inventory.InventoryKey;
import pl.commercelink.inventory.InventoryView;
import pl.commercelink.inventory.MatchedInventory;
import pl.commercelink.inventory.supplier.api.InventoryItem;
import pl.commercelink.pim.api.PimCategory;
import pl.commercelink.products.CatalogPlacement;
import pl.commercelink.products.PimCategoryTree;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class CatalogTargetOptionsFactory {

    static final int MAX_PRODUCTS = 500;

    private final Inventory inventory;
    private final CatalogPlacement catalogPlacement;
    private final PimCategoryTree tree;

    public CatalogTargetOptions build(String storeId, List<String> eans) {
        InventoryView view = inventory.withEnabledSuppliersOnly(storeId);
        List<InventoryKey> keys = new ArrayList<>();
        Set<String> categoryIds = new LinkedHashSet<>();
        for (String ean : new LinkedHashSet<>(eans)) {
            if (keys.size() == MAX_PRODUCTS) {
                break;
            }
            MatchedInventory matched = view.findByEan(ean);
            if (matched == null || matched.isEmpty()) {
                continue;
            }
            keys.add(BrowseRow.catalogKey(matched.getInventoryKey(), ean, mfnOfOfferWith(matched, ean)));
            categoryIds.add(Objects.requireNonNullElse(matched.getTaxonomy().categoryId(), ""));
        }
        CatalogPlacement.StorePlacement placement = catalogPlacement.forStore(storeId);
        List<CatalogTargetOptions.Option> matching = new ArrayList<>();
        Map<String, List<CatalogTargetOptions.Option>> others = new LinkedHashMap<>();
        for (CatalogPlacement.Target target : placement.targets()) {
            int alreadyIn = (int) keys.stream().filter(key -> placement.isIn(target.categoryId(), key)).count();
            boolean fits = target.pimCategoryIds().stream().anyMatch(id -> categoryIds.contains(id.strip()));
            if (fits) {
                matching.add(new CatalogTargetOptions.Option(target.value(), target.label(), alreadyIn));
            } else {
                others.computeIfAbsent(target.catalogName(), name -> new ArrayList<>())
                        .add(new CatalogTargetOptions.Option(target.value(), target.categoryName(), alreadyIn));
            }
        }
        String commonCategory = categoryIds.size() == 1
                ? tree.find(categoryIds.iterator().next()).map(PimCategory::name).orElse(null)
                : null;
        List<CatalogTargetOptions.Group> groups = others.entrySet().stream()
                .map(entry -> new CatalogTargetOptions.Group(entry.getKey(), List.copyOf(entry.getValue())))
                .toList();
        return new CatalogTargetOptions(keys.size(), List.copyOf(matching), groups, preselect(matching, keys.size()),
                placement.targets().isEmpty(), commonCategory);
    }

    /**
     * "Dodaj do katalogu" is offered for a product already in one matching category too, so the first category that still
     * lacks some of the products is chosen; when every one has them all, the first stays chosen.
     */
    private static String preselect(List<CatalogTargetOptions.Option> matching, int count) {
        return matching.stream()
                .filter(option -> option.alreadyIn() < count)
                .findFirst()
                .or(() -> matching.stream().findFirst())
                .map(CatalogTargetOptions.Option::value)
                .orElse(null);
    }

    /** The row adds by the EAN of its cheapest offer and shows that offer's MFN; the offer carrying the EAN has it. */
    private static String mfnOfOfferWith(MatchedInventory matched, String ean) {
        return matched.getInventoryItems().stream()
                .filter(offer -> ean.equals(offer.ean()) && offer.mfn() != null)
                .map(InventoryItem::mfn)
                .findFirst()
                .orElse(null);
    }
}
