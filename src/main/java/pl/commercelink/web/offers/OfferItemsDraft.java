package pl.commercelink.web.offers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import pl.commercelink.baskets.Basket;
import pl.commercelink.baskets.BasketItem;
import pl.commercelink.inventory.Inventory;
import pl.commercelink.inventory.InventoryView;
import pl.commercelink.inventory.MatchedInventory;
import pl.commercelink.pricelist.AvailabilityAndPrice;
import pl.commercelink.pricelist.PricelistFinder;
import pl.commercelink.products.ProductCatalog;
import pl.commercelink.products.ProductCatalogRepository;
import pl.commercelink.web.dtos.AddItemsForm;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Adds the entries of the "add items" dialog to an offer's items, without saving: the table of the details page drafts
 * with it, and the page without JavaScript saves what it added. A price list entry gone since the dialog listed it is
 * skipped instead of failing the whole batch.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OfferItemsDraft {

    private final PricelistFinder pricelistFinder;
    private final Inventory inventory;
    private final ProductCatalogRepository productCatalogRepository;

    /** Returns the number of entries skipped. */
    public int add(Basket basket, AddItemsForm form) {
        String storeId = basket.getStoreId();
        boolean consolidated = !basket.isShowPrices();
        InventoryView inventoryView = null;
        Map<String, Map<String, Integer>> sequenceNumbersByCatalog = new HashMap<>();
        int skipped = 0;

        for (AddItemsForm.Entry entry : form.entries()) {
            if (entry.isFromPricelist()) {
                Optional<AvailabilityAndPrice> found = pricelistFinder.findByPimId(storeId, entry.getCatalogId(), entry.getPimId());
                if (found.isEmpty()) {
                    log.warn("Offer {} (store {}): {} is no longer in price list {}; not added",
                            basket.getBasketId(), storeId, entry.getPimId(), entry.getCatalogId());
                    skipped++;
                    continue;
                }
                basket.addBasketItemInCategoryOrder(BasketItem.of(found.get(), entry.getQty(), entry.getCatalogId(), consolidated),
                        sequenceNumbersByCatalog.computeIfAbsent(entry.getCatalogId(), id -> categorySequenceNumbers(storeId, id)));
            } else {
                if (inventoryView == null) {
                    inventoryView = inventory.withEnabledSuppliersOnly(storeId);
                }
                MatchedInventory matched = inventoryView.findByInventoryKey(entry.inventoryKey());
                basket.addBasketItem(BasketItem.of(matched, entry.getEan(), entry.getQty(), consolidated));
            }
        }
        return skipped;
    }

    private Map<String, Integer> categorySequenceNumbers(String storeId, String catalogId) {
        ProductCatalog catalog = productCatalogRepository.findById(storeId, catalogId);
        return catalog == null ? Map.of() : catalog.getCategorySequenceNumbers();
    }
}
