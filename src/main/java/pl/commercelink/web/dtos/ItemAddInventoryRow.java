package pl.commercelink.web.dtos;

import pl.commercelink.inventory.MatchedInventory;
import pl.commercelink.products.ProductData;
import pl.commercelink.taxonomy.Categories;

/** The product the "add items" dialog lists for a typed EAN, with the name, price and EAN its item gets. */
public record ItemAddInventoryRow(String ean, String mfn, String brand, String name, String category,
                                  double price, long qty, int estimatedDeliveryDays) {

    public static ItemAddInventoryRow of(MatchedInventory matched) {
        ProductData product = matched.getProductData();
        return new ItemAddInventoryRow(product.ean(), product.mfn(), product.brand(), product.name(),
                product.category(), matched.getMedianPrice().grossValue(), matched.getTotalAvailableQty(),
                matched.getEstimatedDeliveryDays());
    }

    public static ItemAddInventoryRow missing(String ean, MatchedInventory matched) {
        return new ItemAddInventoryRow(ean, null, null, "Brak produktu", Categories.UNCATEGORIZED, 0, 0,
                matched.getEstimatedDeliveryDays());
    }
}
