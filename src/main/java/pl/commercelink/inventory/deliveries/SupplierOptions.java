package pl.commercelink.inventory.deliveries;

import org.apache.commons.lang3.StringUtils;
import pl.commercelink.inventory.supplier.api.SupplierOrderOption;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Free-standing so {@link DropshipPurchaseService} can use it without depending on
 * {@link SupplierPurchaseService}.
 */
public final class SupplierOptions {

    private SupplierOptions() {
    }

    public static List<String> missingRequiredOptions(List<SupplierOrderOption> options, Map<String, String> chosen) {
        return options.stream()
                .filter(SupplierOrderOption::required)
                .map(SupplierOrderOption::key)
                .filter(key -> chosen == null || StringUtils.isBlank(chosen.get(key)))
                .toList();
    }

    /** Posted answers with blank values removed - the only filter that needs no declared options. */
    public static Map<String, String> withoutBlankValues(Map<String, String> chosen) {
        Map<String, String> kept = new LinkedHashMap<>();
        if (chosen != null) {
            chosen.forEach((key, value) -> {
                if (!StringUtils.isBlank(value)) {
                    kept.put(key, value);
                }
            });
        }
        return kept;
    }

    /** Records the operator's answers and their human label; options the supplier does not declare are dropped. */
    public static void applySupplierOptions(Delivery delivery, List<SupplierOrderOption> options, Map<String, String> chosen) {
        Map<String, String> kept = new LinkedHashMap<>();
        options.forEach(option -> {
            String value = chosen == null ? null : chosen.get(option.key());
            if (!StringUtils.isBlank(value)) {
                kept.put(option.key(), value);
            }
        });
        delivery.setSupplierOptions(kept);
        delivery.setSupplierOptionsLabel(SupplierOptionsLabel.of(options, kept));
    }
}
