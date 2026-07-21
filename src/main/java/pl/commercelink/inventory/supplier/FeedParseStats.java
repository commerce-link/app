package pl.commercelink.inventory.supplier;

final class FeedParseStats {

    private final String supplierName;
    private int adopted;
    private int pendingAdded;
    private int dropped;

    FeedParseStats(String supplierName) {
        this.supplierName = supplierName;
    }

    void markAdopted() {
        adopted++;
    }

    void markPendingAdded() {
        pendingAdded++;
    }

    void markDropped() {
        dropped++;
    }

    void log() {
        if (adopted + pendingAdded + dropped > 0) {
            System.out.println("Feed " + supplierName + ": adoptedCategories=" + adopted
                    + " pendingAdded=" + pendingAdded + " droppedUnprocessable=" + dropped);
        }
    }
}
