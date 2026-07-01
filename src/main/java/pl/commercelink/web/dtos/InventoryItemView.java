package pl.commercelink.web.dtos;

public record InventoryItemView(
        String supplier,
        String supplierLabel,
        String productEan,
        String productCode,
        double grossPrice,
        int qty
) {
}
