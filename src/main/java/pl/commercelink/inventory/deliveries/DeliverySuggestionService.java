package pl.commercelink.inventory.deliveries;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;
import pl.commercelink.inventory.Inventory;
import pl.commercelink.inventory.InventoryView;
import pl.commercelink.inventory.MatchedInventory;
import pl.commercelink.inventory.supplier.api.InventoryItem;
import pl.commercelink.invoicing.api.Price;
import pl.commercelink.products.ProductCatalog;
import pl.commercelink.products.ProductCatalogRepository;
import pl.commercelink.warehouse.RestockScope;
import pl.commercelink.warehouse.StockLevels;
import pl.commercelink.warehouse.StockProductLevel;
import pl.commercelink.web.dtos.SuggestedDeliveryItem;

import java.util.*;

@Component
@AllArgsConstructor
public class DeliverySuggestionService {

    private final Inventory inventory;
    private final StockLevels stockLevels;
    private final ProductCatalogRepository productCatalogRepository;

    public List<SuggestedDeliveryItem> suggestFor(String storeId, String supplier, Set<String> excludedMfns) {
        InventoryView enabledInventory = inventory.withEnabledSuppliersOnly(storeId);

        List<SuggestedDeliveryItem> suggestions = new ArrayList<>();

        for (ProductCatalog catalog : productCatalogRepository.findAll(storeId)) {
            List<StockProductLevel> levels = stockLevels.calculate(storeId, catalog.getCatalogId(), null, RestockScope.ExpectedStockQty, true);

            for (StockProductLevel level : levels) {
                String mfn = level.getManufacturerCode();
                if (excludedMfns.contains(mfn.toLowerCase())) {
                    continue;
                }

                MatchedInventory matched = enabledInventory.findByProductCode(mfn);
                if (matched == null || matched.isEmpty() || !matched.hasOffersFrom(supplier)) {
                    continue;
                }

                InventoryItem offer = matched.getInventoryItemsFromSupplier(supplier)
                        .stream()
                        .filter(item -> item.netPrice() > 0)
                        .min(Comparator.comparingDouble(InventoryItem::netPrice))
                        .orElse(null);
                if (offer == null) {
                    continue;
                }

                double targetGross = level.getRestockPricePromo();
                double offerGross = Price.fromNet(offer.netPrice()).grossValue();
                if (targetGross <= 0 || offerGross > targetGross) {
                    continue;
                }

                suggestions.add(SuggestedDeliveryItem.of(
                        level.getCategory(),
                        level.getName(),
                        offer.ean(),
                        offer.mfn(),
                        level.getExpectedQuantity(),
                        offer.netPrice()
                ));
            }
        }

        suggestions.sort(Comparator
                .comparing((SuggestedDeliveryItem s) -> s.getCategory() != null ? s.getCategory().name() : "")
                .thenComparing(s -> s.getName() != null ? s.getName() : ""));

        return suggestions;
    }

}
