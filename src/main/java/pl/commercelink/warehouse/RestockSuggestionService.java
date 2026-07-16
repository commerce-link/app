package pl.commercelink.warehouse;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;
import pl.commercelink.inventory.Inventory;
import pl.commercelink.inventory.InventoryView;
import pl.commercelink.inventory.MatchedInventory;
import pl.commercelink.inventory.supplier.api.InventoryItem;
import pl.commercelink.invoicing.api.Price;
import pl.commercelink.products.ProductCatalog;
import pl.commercelink.products.ProductCatalogRepository;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.stores.SupplierScope;
import pl.commercelink.warehouse.api.Warehouse;
import pl.commercelink.warehouse.api.WarehouseItemView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@AllArgsConstructor
public class RestockSuggestionService {

    private final Inventory inventory;
    private final StockLevels stockLevels;
    private final ProductCatalogRepository productCatalogRepository;
    private final StoresRepository storesRepository;
    private final Warehouse warehouse;

    public List<RestockSuggestion> suggestForDelivery(String storeId, String supplier, Set<String> excludedMfns) {
        Store store = storesRepository.findById(storeId);
        if (store == null || !store.isEnabledSupplier(supplier)) {
            return new ArrayList<>();
        }

        List<String> catalogIds = productCatalogRepository.findAll(storeId).stream()
                .map(ProductCatalog::getCatalogId)
                .collect(Collectors.toList());

        List<RestockSuggestion> suggestions = suggest(storeId, catalogIds, null, RestockScope.WholeCatalog, false, supplier, excludedMfns)
                .stream()
                .filter(s -> s.getPriceCategory() != null)
                .collect(Collectors.toList());

        return withoutItemsInStock(storeId, suggestions);
    }

    public List<RestockSuggestion> suggestForRestock(String storeId, String catalogId, String categoryId, RestockScope scope,
                                                     boolean onlyMissingItems, RestockPriceCategory budget) {
        List<RestockSuggestion> suggestions = suggest(storeId, List.of(catalogId), categoryId, scope, onlyMissingItems, null, Collections.emptySet());
        if (budget == null) {
            return suggestions;
        }
        return suggestions.stream()
                .filter(s -> s.isWithinBudget(budget))
                .collect(Collectors.toList());
    }

    private List<RestockSuggestion> suggest(String storeId, List<String> catalogIds, String categoryId, RestockScope scope,
                                            boolean onlyMissingItems, String supplier, Set<String> excludedMfns) {
        InventoryView enabledInventory = inventory.withEnabledSuppliersOnly(storeId, SupplierScope.FULFILMENT);

        Map<String, RestockSuggestion> suggestionsByMfn = new LinkedHashMap<>();

        for (String catalogId : catalogIds) {
            for (StockProductLevel level : stockLevels.calculate(storeId, catalogId, categoryId, scope, onlyMissingItems)) {
                String mfn = level.getManufacturerCode().toLowerCase();
                if (excludedMfns.contains(mfn) || suggestionsByMfn.containsKey(mfn)) {
                    continue;
                }
                if (scope == RestockScope.ExpectedStockQty && !level.qualifiesForRestock()) {
                    continue;
                }

                MatchedInventory matched = enabledInventory.findByProductCode(level.getManufacturerCode());
                InventoryItem offer = findCheapestOffer(matched, supplier);

                suggestionsByMfn.put(mfn, new RestockSuggestion(level, offer, categorize(level, offer)));
            }
        }

        List<RestockSuggestion> suggestions = new ArrayList<>(suggestionsByMfn.values());
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

    private RestockPriceCategory categorize(StockProductLevel level, InventoryItem offer) {
        if (offer == null) {
            return null;
        }
        return level.categorizeOfferPrice(Price.fromNet(offer.netPrice()).grossValue());
    }

    private List<RestockSuggestion> withoutItemsInStock(String storeId, List<RestockSuggestion> suggestions) {
        if (suggestions.isEmpty()) {
            return suggestions;
        }

        Set<String> mfnsToCheck = new HashSet<>();
        for (RestockSuggestion suggestion : suggestions) {
            mfnsToCheck.add(suggestion.getManufacturerCode());
            if (suggestion.getOfferMfn() != null) {
                mfnsToCheck.add(suggestion.getOfferMfn());
            }
        }

        Set<String> inStockMfns = warehouse.stockQueryService(storeId)
                .searchByMfns(storeId, mfnsToCheck)
                .stream()
                .filter(item -> item.getQty() > 0)
                .map(WarehouseItemView::getMfn)
                .filter(Objects::nonNull)
                .map(String::toLowerCase)
                .collect(Collectors.toSet());

        return suggestions.stream()
                .filter(s -> !inStockMfns.contains(s.getManufacturerCode().toLowerCase()))
                .filter(s -> s.getOfferMfn() == null || !inStockMfns.contains(s.getOfferMfn().toLowerCase()))
                .collect(Collectors.toList());
    }

}
