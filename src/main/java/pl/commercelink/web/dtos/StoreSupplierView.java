package pl.commercelink.web.dtos;

import pl.commercelink.inventory.supplier.manual.ManualSupplierInfos;
import pl.commercelink.stores.ConnectionMode;
import pl.commercelink.stores.StoreSupplierConnection;

public record StoreSupplierView(
        String label,
        String modeKey
) {
    public static StoreSupplierView from(StoreSupplierConnection connection) {
        String label = connection.getMode() == ConnectionMode.MANUAL ?
                ManualSupplierInfos.label(connection.getSupplierName()) :
                connection.getSupplierName();
        return new StoreSupplierView(label, "inventory.provider." + connection.getMode().name().toLowerCase());
    }
}
