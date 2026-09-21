package pl.commercelink.web.dtos;

// Shown on the super-admin approval screen, which exists for GLOBAL deliveries only. A GLOBAL
// supplier never routes marketplace orders, so every routed order found in such a delivery is
// there against the marketplace's choice and the screen always warns about it.
public record RoutedOrderView(String orderId, RoutedSupplierView supplier) {
}
