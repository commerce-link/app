package pl.commercelink.warehouse;

import pl.commercelink.warehouse.api.WarehouseItemView;

import java.util.List;

public class StockProductLevel {

    private String category;
    private String manufacturerCode;
    private String name;
    private int restockPriceLowest;
    private int restockPriceHotDeal;
    private int restockPricePromo;
    private int restockPriceStandard;
    private int expectedQuantity;
    private int currentQuantity;
    private int missingQuantity;

    private int tolerance;
    private int diff;

    public StockProductLevel(String category, String manufacturerCode, String name, int restockPricePromo, int restockPriceStandard, int expectedQuantity) {
        this.category = category;
        this.manufacturerCode = manufacturerCode;
        this.name = name;
        this.restockPricePromo = restockPricePromo;
        this.restockPriceStandard = restockPriceStandard;
        this.expectedQuantity = expectedQuantity;
        this.tolerance = 1;
    }

    public void calculateStock(List<WarehouseItemView> warehouseItem) {
        this.currentQuantity = warehouseItem.stream()
                .map(WarehouseItemView::getQty)
                .reduce(0, Integer::sum);

        this.missingQuantity = expectedQuantity - currentQuantity;
        this.diff = Math.abs(expectedQuantity - currentQuantity);
    }

    public boolean isFullyMissing() {
        return currentQuantity == 0 && expectedQuantity > 0;
    }

    public boolean qualifiesForRestock() {
        return missingQuantity > 0;
    }

    public RestockPriceCategory categorizeOfferPrice(double offerGross) {
        if (isWithinBudget(offerGross, RestockPriceCategory.HotDeal)) {
            return RestockPriceCategory.HotDeal;
        }
        if (isWithinBudget(offerGross, RestockPriceCategory.LowestPrice)) {
            return RestockPriceCategory.LowestPrice;
        }
        if (isWithinBudget(offerGross, RestockPriceCategory.GoodDeal)) {
            return RestockPriceCategory.GoodDeal;
        }
        return null;
    }

    public boolean isWithinBudget(double offerGross, RestockPriceCategory budget) {
        int threshold = restockPriceFor(budget);
        return threshold > 0 && offerGross <= threshold;
    }

    public int restockPriceFor(RestockPriceCategory budget) {
        return switch (budget) {
            case HotDeal -> restockPriceHotDeal;
            case LowestPrice -> restockPriceLowest;
            case GoodDeal -> restockPricePromo;
            case Standard -> restockPriceStandard;
        };
    }

    public String getCategory() {
        return category;
    }

    public String getManufacturerCode() {
        return manufacturerCode;
    }

    public String getName() {
        return name;
    }

    public int getExpectedQuantity() {
        return expectedQuantity;
    }

    public int getCurrentQuantity() {
        return currentQuantity;
    }

    public int getMissingQuantity() {
        return missingQuantity;
    }

    public int getRestockPricePromo() {
        return restockPricePromo;
    }

    public int getRestockPriceStandard() {
        return restockPriceStandard;
    }

    public boolean hasManufacturerCode(String other) {
        return this.manufacturerCode.equalsIgnoreCase(other);
    }

    public void setRestockPricePromo(int restockPricePromo) {
        this.restockPricePromo = restockPricePromo;
    }

    public void setRestockPriceStandard(int restockPriceStandard) {
        this.restockPriceStandard = restockPriceStandard;
    }

    public void setExpectedQuantity(int expectedQuantity) {
        this.expectedQuantity = expectedQuantity;
    }

    public boolean isInStockAtRightQuantity() {
        return currentQuantity > 0 && expectedQuantity > 0 && diff <= tolerance;
    }

    public boolean isInStockAtExcessQuantity() {
        return currentQuantity > 0 && expectedQuantity > 0 && missingQuantity < 0 && Math.abs(missingQuantity) > tolerance;
    }

    public boolean isInStockAtShortageQuantity() {
        return currentQuantity > 0 && expectedQuantity > 0 && missingQuantity > 0 && Math.abs(missingQuantity) > tolerance;
    }

    public boolean isInStockButNoLongerNeeded() {
        return currentQuantity > 0 && expectedQuantity == 0;
    }

    public boolean isNotInStock() {
        return currentQuantity == 0 && expectedQuantity > 0;
    }

    public int getRestockPriceLowest() {
        return restockPriceLowest;
    }

    public void setRestockPriceLowest(int restockPriceLowest) {
        this.restockPriceLowest = restockPriceLowest;
    }

    public int getRestockPriceHotDeal() {
        return restockPriceHotDeal;
    }

    public void setRestockPriceHotDeal(int restockPriceHotDeal) {
        this.restockPriceHotDeal = restockPriceHotDeal;
    }

}
