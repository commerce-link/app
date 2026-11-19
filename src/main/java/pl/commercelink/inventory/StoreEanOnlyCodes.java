package pl.commercelink.inventory;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.commercelink.pim.api.PimCatalog;

/**
 * The brand and code pairs of one store's own PIM entries (including clones) flagged to be joined by EAN only.
 * Computed on demand from the current PIM index; nothing is kept between calls.
 */
@Component
@RequiredArgsConstructor
public class StoreEanOnlyCodes {

    private final PimCatalog pimCatalog;
    private final InventoryMatchingProperties matchingProperties;

    public VariantCodes of(String storeId) {
        // the store pairs add to the global ones, so they are off whenever the global switch is off
        if (!matchingProperties.pimEanOnlyFlag() || !matchingProperties.pimStoreEanOnlyFlag()
                || storeId == null || storeId.isBlank()) {
            return VariantCodes.none();
        }
        return PimEanOnlyCodes.ofStore(storeId, pimCatalog.withStoreData(storeId).findAll());
    }
}
