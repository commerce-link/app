package pl.commercelink.web.dtos;

import pl.commercelink.inventory.supplier.manual.ManualSupplierInfos;
import pl.commercelink.orders.Order;
import pl.commercelink.stores.ConnectionMode;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoreSupplierConnection;

public record RoutedSupplierView(
        String externalSupplierId,
        String supplierName,
        String modeKey,
        boolean enabled,
        boolean includeInFulfilment
) {

    public static RoutedSupplierView from(Order order, Store store) {
        if (!order.isBoundToExternalSupplier()) {
            return null;
        }
        String externalSupplierId = order.getExternalSupplierId();
        StoreSupplierConnection connection = store == null ? null : store.getSupplierConnections().stream()
                .filter(c -> externalSupplierId.equals(c.routingExternalSupplierId()))
                .findFirst()
                .orElse(null);
        if (connection == null) {
            return unmatched(externalSupplierId);
        }
        String label = connection.getMode() == ConnectionMode.MANUAL
                ? ManualSupplierInfos.label(connection.getSupplierName())
                : connection.getSupplierName();
        return new RoutedSupplierView(
                externalSupplierId,
                label,
                "inventory.provider." + connection.getMode().name().toLowerCase(),
                connection.isEnabled(),
                connection.isIncludeInFulfilment());
    }

    private static RoutedSupplierView unmatched(String externalSupplierId) {
        return new RoutedSupplierView(externalSupplierId, null, null, false, false);
    }

    public boolean isMatched() {
        return supplierName != null;
    }
}
