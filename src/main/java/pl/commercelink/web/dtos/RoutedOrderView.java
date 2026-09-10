package pl.commercelink.web.dtos;

public record RoutedOrderView(String orderId, RoutedSupplierView supplier, boolean deliveryMatches) {
}
