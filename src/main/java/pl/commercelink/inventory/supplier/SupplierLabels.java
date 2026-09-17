package pl.commercelink.inventory.supplier;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoreSupplierConnection;
import pl.commercelink.stores.StoresRepository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/** Resolves what the operator should see for a supplier connection identity. */
@Component
@RequiredArgsConstructor
public class SupplierLabels {

    private final StoresRepository storesRepository;

    public static String labelOf(StoreSupplierConnection connection) {
        String label = StringUtils.trimToNull(connection.getLabel());
        return label != null ? label : SupplierIdentity.legacyLabel(connection.getSupplierName());
    }

    public SupplierLabelMap forStore(Store store) {
        Map<String, String> byKey = new LinkedHashMap<>();
        List<SupplierLabelMap.Option> options = new ArrayList<>();
        if (store != null) {
            for (StoreSupplierConnection connection : store.getSupplierConnections()) {
                byKey.put(SupplierLabelMap.key(store.getStoreId(), connection.getSupplierName()), labelOf(connection));
                if (connection.isEnabled()) {
                    options.add(new SupplierLabelMap.Option(connection.getSupplierName(), labelOf(connection)));
                }
            }
        }
        options.sort(Comparator.comparing(SupplierLabelMap.Option::label, String.CASE_INSENSITIVE_ORDER));
        return new SupplierLabelMap(store == null ? null : store.getStoreId(), byKey, List.copyOf(options));
    }

    public SupplierLabelMap forStoreId(String storeId) {
        return forStore(storesRepository.findById(storeId));
    }

    public SupplierLabelMap forStoreIds(Collection<String> storeIds) {
        Map<String, String> byKey = new LinkedHashMap<>();
        for (String storeId : new LinkedHashSet<>(storeIds)) {
            SupplierLabelMap one = forStoreId(storeId);
            byKey.putAll(one.entries());
        }
        return new SupplierLabelMap(null, byKey, List.of());
    }
}
