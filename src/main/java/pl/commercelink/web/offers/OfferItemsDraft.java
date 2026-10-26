package pl.commercelink.web.offers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
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
 * with it, and the page without JavaScript saves what it added. An entry whose product is gone since the dialog listed it
 * (no longer in the price list, or unknown at the suppliers) is skipped instead of failing the whole batch.
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
                if (!addable(matched)) {
                    log.warn("Offer {} (store {}): EAN {} / code {} is not at the suppliers; not added",
                            basket.getBasketId(), storeId, entry.getEan(), entry.getMfn());
                    skipped++;
                    continue;
                }
                basket.addBasketItem(BasketItem.of(matched, entry.getQty(), consolidated));
            }
        }
        return skipped;
    }

    /**
     * Without offers BasketItem.of makes a "missing product" item named by the manufacturer code, as it always did; an
     * entry known only by its EAN has no code to name it by: the item would have a blank code, which the next table
     * save drops anyway.
     */
    private static boolean addable(MatchedInventory matched) {
        return matched != null && (matched.hasAnyOffers()
                || matched.getInventoryKey().getProductCodes().stream().anyMatch(StringUtils::isNotBlank));
    }

    private Map<String, Integer> categorySequenceNumbers(String storeId, String catalogId) {
        ProductCatalog catalog = productCatalogRepository.findById(storeId, catalogId);
        return catalog == null ? Map.of() : catalog.getCategorySequenceNumbers();
    }
}
