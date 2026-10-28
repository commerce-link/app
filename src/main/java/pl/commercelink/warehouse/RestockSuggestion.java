package pl.commercelink.warehouse;

import lombok.RequiredArgsConstructor;
import pl.commercelink.inventory.supplier.api.InventoryItem;
import pl.commercelink.invoicing.api.Price;

@RequiredArgsConstructor
public class RestockSuggestion {

    private final StockProductLevel level;
    private final InventoryItem offer;
    private final RestockPriceCategory priceCategory;

    public String getCategory() {
        return level.getCategory();
    }

    public String getName() {
        return level.getName();
    }

    public String getManufacturerCode() {
        return level.getManufacturerCode();
    }

    public int getExpectedQuantity() {
        return level.getExpectedQuantity();
    }

    public int getMissingQuantity() {
        return level.getMissingQuantity();
    }

    public RestockPriceCategory getPriceCategory() {
        return priceCategory;
    }

    public boolean hasOffer() {
        return offer != null;
    }

    public String getEan() {
        return hasOffer() ? offer.ean() : null;
    }

    public String getOfferMfn() {
        return hasOffer() ? offer.mfn() : null;
    }

    public double getNetPrice() {
        return hasOffer() ? offer.netPrice() : 0;
    }

    public int getAvailableQty() {
        return hasOffer() ? offer.qty() : 0;
    }

    public double offerVsAggregatePriceRatio() {
        if (!hasOffer() || priceCategory == null) {
            return Double.MAX_VALUE;
        }
        double aggregatePrice = level.restockPriceFor(priceCategory);
        if (aggregatePrice <= 0) {
            return Double.MAX_VALUE;
        }
        return offer.netPrice() / aggregatePrice;
    }

    public boolean isWithinBudget(RestockPriceCategory budget) {
        return hasOffer() && level.isWithinBudget(offer.netPrice(), budget);
    }

    public double restockPriceGrossFor(RestockPriceCategory budget) {
        return Price.fromNet(level.restockPriceFor(budget)).grossValue();
    }
}
