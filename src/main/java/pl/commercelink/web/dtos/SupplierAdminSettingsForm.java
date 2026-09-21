package pl.commercelink.web.dtos;

import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import pl.commercelink.stores.Store;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Supplier settings of a store that only the application admin changes: whether the store may use the global supplier
 * configuration, and how long its inventory is cached (empty = the default). The cache time is taken as text so a
 * mistyped value gets a message at the field.
 */
@Getter
@Setter
public class SupplierAdminSettingsForm {

    public static final int MAX_CACHE_TTL_MINUTES = 1440;

    private boolean canUseGlobalSuppliers;
    private String inventoryCacheTtlMinutes;

    public static SupplierAdminSettingsForm from(Store store) {
        SupplierAdminSettingsForm form = new SupplierAdminSettingsForm();
        form.canUseGlobalSuppliers = store.canUseGlobalSuppliers();
        form.inventoryCacheTtlMinutes = store.getInventoryCacheTtlMinutes().map(String::valueOf).orElse("");
        return form;
    }

    public Map<String, String> validate() {
        Map<String, String> errors = new LinkedHashMap<>();
        String ttl = StringUtils.trimToNull(inventoryCacheTtlMinutes);
        if (ttl != null && (!ttl.matches("\\d{1,4}") || Integer.parseInt(ttl) < 1 || Integer.parseInt(ttl) > MAX_CACHE_TTL_MINUTES)) {
            errors.put("inventoryCacheTtlMinutes", "store.suppliers.admin.cacheTtl.invalid");
        }
        return errors;
    }

    /** The cache time to save, null for the default; call after validate(). */
    public Integer cacheTtlMinutes() {
        String ttl = StringUtils.trimToNull(inventoryCacheTtlMinutes);
        return ttl == null ? null : Integer.valueOf(ttl);
    }
}
