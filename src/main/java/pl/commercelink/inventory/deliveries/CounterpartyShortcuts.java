package pl.commercelink.inventory.deliveries;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import pl.commercelink.inventory.supplier.SupplierIdentity;
import pl.commercelink.inventory.supplier.SupplierLabels;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoreSupplierConnection;

/**
 * Which counterparty (by its shortcut in the invoicing system) a delivery belongs to. The delivery
 * provider is a connection identity and is never the shortcut itself any more; the shortcut is
 * learned from an invoice sync, configured on the connection, or defaults to the supplier type.
 */
@Component
public class CounterpartyShortcuts {

    public String forDelivery(Store store, Delivery delivery) {
        String synced = StringUtils.trimToNull(delivery.getCounterpartyShortcut());
        if (synced != null) {
            return synced;
        }
        String provider = delivery.getProvider();
        StoreSupplierConnection connection = store == null ? null : store.getSupplierConnections().stream()
                .filter(candidate -> candidate.getSupplierName().equals(provider))
                .findFirst()
                .orElse(null);
        if (connection != null) {
            String configured = StringUtils.trimToNull(connection.getBillingShortcut());
            if (configured != null) {
                return configured;
            }
            if (SupplierIdentity.hasToken(provider)) {
                return SupplierIdentity.isManual(provider)
                        ? SupplierLabels.labelOf(connection)
                        : SupplierIdentity.typeOf(provider);
            }
        }
        // Legacy identities and deliveries whose provider was overwritten by an old invoice sync
        // keep resolving exactly as before this feature.
        return provider;
    }
}
