package pl.commercelink.inventory.search;

import pl.commercelink.stores.ConnectionMode;

public record OfferRow(String supplier, String supplierLabel, ConnectionMode mode, String productEan, String productCode,
                       double grossPrice, int qty, boolean cheapest, CodeMatch codeMatch) {

    public boolean hasStock() {
        return qty > 0;
    }

    public boolean hasPrice() {
        return grossPrice > 0;
    }
}
