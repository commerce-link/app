package pl.commercelink.inventory.deliveries;

import java.util.List;

public record PurchaseValidation(String provider, String purchaseRef, String currency,
                                 double totalNet, boolean fullyAvailable,
                                 List<Line> lines) {

    public record Line(String name, String ean, String mfn, int requestedQty,
                       int availableQty, double feedUnitCost, double liveUnitCost) {

        public boolean isAvailable() { return availableQty >= requestedQty; }
        public int getMissingQty() { return Math.max(0, requestedQty - availableQty); }
        public double getPriceDelta() { return liveUnitCost - feedUnitCost; }
    }
}
