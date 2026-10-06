package pl.commercelink.web.inventory;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.commercelink.inventory.Inventory;
import pl.commercelink.inventory.InventoryKey;
import pl.commercelink.inventory.InventoryView;
import pl.commercelink.inventory.MatchedInventory;
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
public class AddToCatalogDialogFactory {

    static final int MAX_PRODUCTS = 500;
    private static final String DEFAULT_RETURN = BrowseQuery.start().href();

    private final Inventory inventory;
    private final CatalogPlacement catalogPlacement;
    private final PimCategoryTree tree;

    public AddToCatalogDialog build(String storeId, List<String> eans, String returnTo) {
        InventoryView view = inventory.withEnabledSuppliersOnly(storeId);
        List<String> found = new ArrayList<>();
        List<InventoryKey> keys = new ArrayList<>();
        Set<String> categoryIds = new LinkedHashSet<>();
        String firstName = null;
        for (String ean : new LinkedHashSet<>(eans)) {
            if (found.size() == MAX_PRODUCTS) {
                break;
            }
            MatchedInventory matched = view.findByEan(ean);
            if (matched == null || matched.isEmpty()) {
                continue;
            }
            found.add(ean);
            keys.add(matched.getInventoryKey());
            categoryIds.add(Objects.requireNonNullElse(matched.getTaxonomy().categoryId(), ""));
            if (firstName == null) {
                firstName = matched.getTaxonomy().name();
            }
        }
        CatalogPlacement.StorePlacement placement = catalogPlacement.forStore(storeId);
        List<AddToCatalogDialog.Option> matching = new ArrayList<>();
        Map<String, List<AddToCatalogDialog.Option>> others = new LinkedHashMap<>();
        for (CatalogPlacement.Target target : placement.targets()) {
            int alreadyIn = (int) keys.stream().filter(key -> placement.isIn(target.categoryId(), key)).count();
            boolean fits = target.pimCategoryIds().stream().anyMatch(id -> categoryIds.contains(id.strip()));
            if (fits) {
                matching.add(new AddToCatalogDialog.Option(target.value(), target.label(), alreadyIn, false));
            } else {
                others.computeIfAbsent(target.catalogName(), name -> new ArrayList<>())
                        .add(new AddToCatalogDialog.Option(target.value(), target.categoryName(), alreadyIn, false));
            }
        }
        List<AddToCatalogDialog.Option> preselected = preselect(matching, keys.size());
        String commonCategory = categoryIds.size() == 1
                ? tree.find(categoryIds.iterator().next()).map(PimCategory::name).orElse(null)
                : null;
        List<AddToCatalogDialog.Group> groups = others.entrySet().stream()
                .map(entry -> new AddToCatalogDialog.Group(entry.getKey(), List.copyOf(entry.getValue())))
                .toList();
        return new AddToCatalogDialog(List.copyOf(found), found.size() == 1 ? firstName : null, commonCategory,
                preselected, groups, placement.targets().isEmpty(),
                InventoryReturnTo.safe(returnTo).orElse(DEFAULT_RETURN), AddToCatalogDialog.ACTION);
    }

    /**
     * "Dodaj do innej" opens this dialog for a product already in one matching category, so the first category that still
     * lacks some of the products is checked; when every one has them all, the first stays checked.
     */
    private static List<AddToCatalogDialog.Option> preselect(List<AddToCatalogDialog.Option> matching, int count) {
        int chosen = 0;
        for (int i = 0; i < matching.size(); i++) {
            if (matching.get(i).alreadyIn() < count) {
                chosen = i;
                break;
            }
        }
        List<AddToCatalogDialog.Option> options = new ArrayList<>();
        for (int i = 0; i < matching.size(); i++) {
            AddToCatalogDialog.Option option = matching.get(i);
            options.add(new AddToCatalogDialog.Option(option.value(), option.label(), option.alreadyIn(), i == chosen));
        }
        return List.copyOf(options);
    }
}
