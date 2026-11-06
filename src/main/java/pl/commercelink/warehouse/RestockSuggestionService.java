package pl.commercelink.warehouse;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;
import pl.commercelink.inventory.Inventory;
import pl.commercelink.inventory.InventoryView;
import pl.commercelink.inventory.MatchedInventory;
import pl.commercelink.inventory.supplier.InventoryItem;
import pl.commercelink.products.ProductCatalog;
import pl.commercelink.products.ProductCatalogRepository;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.stores.SupplierScope;
import pl.commercelink.warehouse.api.Warehouse;
import pl.commercelink.warehouse.api.WarehouseItemView;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@AllArgsConstructor
public class RestockSuggestionService {

    private static final int MAX_DELIVERY_SUGGESTIONS = 25;

    private final Inventory inventory;
    private final StockLevels stockLevels;
    private final ProductCatalogRepository productCatalogRepository;
    private final StoresRepository storesRepository;
    private final Warehouse warehouse;

    public List<RestockSuggestion> suggestForDelivery(String storeId, String supplier, Set<String> excludedEans) {
        Store store = storesRepository.findById(storeId);
        if (store == null || !store.isEnabledSupplier(supplier)) {
            return new ArrayList<>();
        }

        List<String> catalogIds = productCatalogRepository.findAll(storeId).stream()
                .map(ProductCatalog::getCatalogId)
                .collect(Collectors.toList());

        List<RestockSuggestion> suggestions = suggest(storeId, catalogIds, null, RestockScope.WholeCatalog, false, supplier, excludedEans, true)
                .stream()
                .filter(s -> s.getPriceCategory() != null)
                .sorted(Comparator.comparing(RestockSuggestion::getPriceCategory)
                        .thenComparingDouble(RestockSuggestion::offerVsAggregatePriceRatio))
                .limit(MAX_DELIVERY_SUGGESTIONS)
                .collect(Collectors.toList());

        return withoutItemsInStock(storeId, suggestions);
    }

    public List<RestockSuggestion> suggestForRestock(String storeId, String catalogId, String categoryId, RestockScope scope,
                                                     boolean onlyMissingItems, RestockPriceCategory budget) {
        List<RestockSuggestion> suggestions = suggest(storeId, List.of(catalogId), categoryId, scope, onlyMissingItems, null, Collections.emptySet(), false);
        if (budget == null) {
            return suggestions;
        }
        return suggestions.stream()
                .filter(s -> s.isWithinBudget(budget))
                .collect(Collectors.toList());
    }

    private List<RestockSuggestion> suggest(String storeId, List<String> catalogIds, String categoryId, RestockScope scope,
                                            boolean onlyMissingItems, String supplier, Set<String> excludedEans,
                                            boolean onlyDeliverySuggestionCategories) {
        InventoryView enabledInventory = inventory.withEnabledSuppliersOnly(storeId, SupplierScope.FULFILMENT);

        List<StockProductLevel> suggestedLevels = new ArrayList<>();
        List<RestockSuggestion> suggestions = new ArrayList<>();

        for (String catalogId : catalogIds) {
            for (StockProductLevel level : stockLevels.calculate(storeId, catalogId, categoryId, scope, onlyMissingItems, onlyDeliverySuggestionCategories)) {
                if (suggestedLevels.stream().anyMatch(s -> s.inventoryKey().matches(level.inventoryKey()))) {
                    continue;
                }
                if (scope == RestockScope.ExpectedStockQty && !level.qualifiesForRestock()) {
                    continue;
                }

                MatchedInventory matched = enabledInventory.findByInventoryKey(level.inventoryKey());
                Collection<String> productEans = productEans(level, matched);
                if (productEans.stream().anyMatch(excludedEans::contains)) {
                    continue;
                }
                InventoryItem offer = findCheapestOffer(matched, supplier);

                suggestedLevels.add(level);
                suggestions.add(new RestockSuggestion(level, offer, categorize(level, offer), productEans));
            }
        }

        suggestions.sort(Comparator
                .comparing((RestockSuggestion s) -> s.getCategory() != null ? s.getCategory() : "")
                .thenComparing(s -> s.getName() != null ? s.getName() : ""));
        return suggestions;
    }

    private InventoryItem findCheapestOffer(MatchedInventory matched, String supplier) {
        if (matched == null || matched.isEmpty()) {
            return null;
        }
        List<InventoryItem> offers = supplier != null
                ? matched.getInventoryItemsFromSupplier(supplier)
                : matched.getInventoryItems();
        return offers.stream()
                .filter(item -> item.netPrice() > 0)
                .min(Comparator.comparingDouble(InventoryItem::netPrice))
                .orElse(null);
    }

    private static Collection<String> productEans(StockProductLevel level, MatchedInventory matched) {
        return matched == null ? level.inventoryKey().getProductEans() : matched.getInventoryKey().getProductEans();
    }

    private RestockPriceCategory categorize(StockProductLevel level, InventoryItem offer) {
        if (offer == null) {
            return null;
        }
        return level.categorizeOfferPrice(offer.netPrice());
    }

    private List<RestockSuggestion> withoutItemsInStock(String storeId, List<RestockSuggestion> suggestions) {
        if (suggestions.isEmpty()) {
            return suggestions;
        }

        Set<String> eansToCheck = suggestions.stream()
                .flatMap(s -> s.productEans().stream())
                .collect(Collectors.toSet());

        Set<String> inStockEans = warehouse.stockQueryService(storeId)
                .searchByEans(storeId, eansToCheck)
                .stream()
                .filter(item -> item.getQty() > 0)
                .map(WarehouseItemView::getEan)
                .collect(Collectors.toSet());

        return suggestions.stream()
                .filter(s -> s.productEans().stream().noneMatch(inStockEans::contains))
                .collect(Collectors.toList());
    }

}
