package pl.commercelink.inventory;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.commercelink.inventory.supplier.api.InventoryItem;
import pl.commercelink.stores.SupplierScope;

import java.util.List;
import java.util.Objects;

import static pl.commercelink.taxonomy.UnifiedProductIdentifiers.areEansEq;
import static pl.commercelink.taxonomy.UnifiedProductIdentifiers.areMfnsEq;

@Component
@RequiredArgsConstructor
public class SupplierSkuResolver {

    private final Inventory inventory;

    public StoreSkuLookup forStore(String storeId, String provider) {
        InventoryView view = inventory.withEnabledSuppliersOnly(storeId, SupplierScope.FULFILMENT);
        return (ean, mfn) -> findSku(view, provider, ean, mfn);
    }

    @FunctionalInterface
    public interface StoreSkuLookup {
        String skuFor(String ean, String mfn);
    }

    private static String findSku(InventoryView view, String provider, String ean, String mfn) {
        List<InventoryItem> candidates = view.findByInventoryKey(new InventoryKey(ean, mfn))
                .getInventoryItemsFromSupplier(provider);
        return candidates.stream()
                .filter(item -> areEansEq(item.ean(), ean) && areMfnsEq(item.mfn(), mfn))
                .map(InventoryItem::sku)
                .filter(Objects::nonNull)
                .findFirst()
                .or(() -> candidates.stream()
                        .filter(item -> areEansEq(item.ean(), ean))
                        .map(InventoryItem::sku)
                        .filter(Objects::nonNull)
                        .findFirst())
                .orElse(null);
    }
}
