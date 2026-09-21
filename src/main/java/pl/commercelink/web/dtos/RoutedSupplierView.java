package pl.commercelink.web.dtos;

import pl.commercelink.inventory.supplier.SupplierIdentity;
import pl.commercelink.inventory.supplier.SupplierLabels;
import pl.commercelink.orders.Order;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoreSupplierConnection;

public record RoutedSupplierView(
        String externalSupplierId,
        String supplierName,
        String providerType,
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
        String label = SupplierLabels.labelOf(connection);
        return new RoutedSupplierView(
                externalSupplierId,
                label,
                SupplierIdentity.typeOf(connection.getSupplierName()),
                "inventory.provider." + connection.getMode().name().toLowerCase(),
                connection.isEnabled(),
                connection.isIncludeInFulfilment());
    }

    private static RoutedSupplierView unmatched(String externalSupplierId) {
        return new RoutedSupplierView(externalSupplierId, null, null, null, false, false);
    }

    public boolean isMatched() {
        return supplierName != null;
    }
}
