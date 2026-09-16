package pl.commercelink.web.dtos;

import pl.commercelink.inventory.supplier.SupplierLabels;
import pl.commercelink.stores.StoreSupplierConnection;

public record StoreSupplierView(
        String label,
        String modeKey
) {
    public static StoreSupplierView from(StoreSupplierConnection connection) {
        String label = SupplierLabels.labelOf(connection);
        return new StoreSupplierView(label, "inventory.provider." + connection.getMode().name().toLowerCase());
    }
}
