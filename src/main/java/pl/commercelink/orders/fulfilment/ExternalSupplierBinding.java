package pl.commercelink.orders.fulfilment;

import pl.commercelink.orders.Order;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoreSupplierConnection;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Predicate;

class ExternalSupplierBinding implements Predicate<FulfilmentItem> {

    private final Map<String, String> externalSupplierIdByOrderId;
    private final Map<String, String> externalSupplierIdBySupplierName;

    private ExternalSupplierBinding(Map<String, String> externalSupplierIdByOrderId,
                                    Map<String, String> externalSupplierIdBySupplierName) {
        this.externalSupplierIdByOrderId = externalSupplierIdByOrderId;
        this.externalSupplierIdBySupplierName = externalSupplierIdBySupplierName;
    }

    static ExternalSupplierBinding of(Store store, Collection<Order> orders) {
        Map<String, String> byOrderId = new HashMap<>();
        for (Order order : orders) {
            if (order.isBoundToExternalSupplier()) {
                byOrderId.put(order.getOrderId(), order.getExternalSupplierId());
            }
        }

        Map<String, String> bySupplierName = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        List<StoreSupplierConnection> connections = store != null ? store.getSupplierConnections() : List.of();
        for (StoreSupplierConnection connection : connections) {
            if (connection.getExternalSupplierId() != null) {
                bySupplierName.put(connection.getSupplierName(), connection.getExternalSupplierId());
            }
        }

        return new ExternalSupplierBinding(byOrderId, bySupplierName);
    }

    @Override
    public boolean test(FulfilmentItem candidate) {
        String required = externalSupplierIdByOrderId.get(candidate.getAllocation().getOrderId());
        if (required == null) {
            return true;
        }
        return required.equals(externalSupplierIdBySupplierName.get(candidate.getSource().getProvider()));
    }
}
