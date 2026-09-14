package pl.commercelink.inventory.search;

import pl.commercelink.stores.ConnectionMode;

public record OfferRow(String supplier, String supplierLabel, ConnectionMode mode, String productEan, String productCode,
                       double netPrice, double grossPrice, int qty, int deliveryDays, boolean cheapest,
                       Double percentAboveCheapest) {

    public boolean hasStock() {
        return qty > 0;
    }

    public boolean hasPrice() {
        return netPrice > 0;
    }

    public boolean hasDeliveryDays() {
        return deliveryDays > 0;
    }
}
