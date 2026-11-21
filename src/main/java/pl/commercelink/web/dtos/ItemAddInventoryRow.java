package pl.commercelink.web.dtos;

import org.apache.commons.lang3.StringUtils;
import pl.commercelink.inventory.MatchedInventory;
import pl.commercelink.inventory.supplier.InventoryItem;
import pl.commercelink.products.ProductData;
import pl.commercelink.taxonomy.Categories;

import java.util.List;
import java.util.stream.Stream;

import static pl.commercelink.taxonomy.UnifiedProductIdentifiers.areEansEq;

/** The product the "add items" dialog lists for a typed EAN, with the name, price and EAN its item gets. */
public record ItemAddInventoryRow(String ean, String mfn, String brand, String name, String category,
                                  double price, long qty, int estimatedDeliveryDays) {

    public static ItemAddInventoryRow of(String typedEan, MatchedInventory matched) {
        ProductData product = matched.getProductData();
        return new ItemAddInventoryRow(itemEan(typedEan, product, matched), product.mfn(), product.brand(),
                product.name(), product.category(), matched.getMedianPrice().grossValue(),
                matched.getTotalAvailableQty(), matched.getEstimatedDeliveryDays());
    }

    public static ItemAddInventoryRow missing(String ean, MatchedInventory matched) {
        return new ItemAddInventoryRow(ean, null, null, "Brak produktu", Categories.UNCATEGORIZED, 0, 0,
                matched.getEstimatedDeliveryDays());
    }

    // The dialog posts this EAN back: the item is looked up again and saved under it, and a supplier is ordered from by
    // it. The primary GTIN of a PIM entry may be one no offer carries, so it is taken only when an offer does (one EAN
    // per product, whichever EAN was typed, so the dialog does not list the product twice); otherwise the typed EAN,
    // which repeats this very lookup; otherwise one an offer carries.
    private static String itemEan(String typedEan, ProductData product, MatchedInventory matched) {
        List<InventoryItem> offers = matched.getInventoryItems();
        return Stream.of(product.ean(), typedEan)
                .filter(StringUtils::isNotBlank)
                .filter(ean -> offers.stream().anyMatch(offer -> areEansEq(offer.ean(), ean)))
                .findFirst()
                .or(() -> offers.stream().map(InventoryItem::ean).filter(StringUtils::isNotBlank).findFirst())
                .orElse(typedEan);
    }
}
