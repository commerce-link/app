package pl.commercelink.inventory.supplier;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Identity → label lookup for one store (default store id) or several (explicit store id). */
public final class SupplierLabelMap {

    public record Option(String identity, String label) {
    }

    // Separator unlikely to appear in a store id or a supplier identity, keeping the composite key unambiguous.
    private static final char KEY_SEPARATOR = '\u0001';

    private final String defaultStoreId;
    private final Map<String, String> byKey;
    private final List<Option> options;

    SupplierLabelMap(String defaultStoreId, Map<String, String> byKey, List<Option> options) {
        this.defaultStoreId = defaultStoreId;
        this.byKey = Map.copyOf(byKey);
        this.options = options;
    }

    static String key(String storeId, String identity) {
        return storeId + KEY_SEPARATOR + identity;
    }

    public String of(String identity) {
        return of(defaultStoreId, identity);
    }

    /** Falls back to the legacy label shape, then to the identity itself (e.g. `Warehouse`, a removed connection). */
    public String of(String storeId, String identity) {
        if (identity == null) {
            return null;
        }
        String label = byKey.get(key(storeId, identity));
        return label != null ? label : SupplierIdentity.legacyLabel(identity);
    }

    public boolean has(String identity) {
        return identity != null && byKey.containsKey(key(defaultStoreId, identity));
    }

    /**
     * The same map where the store's own warehouse ({@link SupplierRegistry#WAREHOUSE}, a technical identity in English)
     * reads as the given text, e.g. "Magazyn sklepu" on the order screens and printouts. The select options stay as
     * they are: the warehouse is not a connection to choose.
     */
    public SupplierLabelMap withWarehouse(String label) {
        if (label == null) {
            return this;
        }
        Map<String, String> copy = new LinkedHashMap<>(byKey);
        copy.put(key(defaultStoreId, SupplierRegistry.WAREHOUSE), label);
        return new SupplierLabelMap(defaultStoreId, copy, options);
    }

    /** Enabled connections of the default store, for select inputs. */
    public List<Option> options() {
        return options;
    }

    Map<String, String> entries() {
        return byKey;
    }
}
