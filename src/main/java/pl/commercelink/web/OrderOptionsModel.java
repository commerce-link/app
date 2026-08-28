package pl.commercelink.web;

import org.apache.commons.lang3.StringUtils;
import org.springframework.ui.Model;
import pl.commercelink.inventory.deliveries.SupplierPurchaseService;
import pl.commercelink.inventory.supplier.api.SupplierOrderOption;
import pl.commercelink.inventory.supplier.api.SupplierOrderOptionsContext;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared helper that exposes a supplier's order options - and any already chosen or defaulted values -
 * to a purchase/approval confirmation model. Reused by {@link DeliveriesController} and {@link DropshipController}
 * so both screens present the same options widget.
 */
public final class OrderOptionsModel {

    private OrderOptionsModel() {
    }

    public static void addOrderOptions(SupplierPurchaseService supplierPurchaseService, String storeId, String provider,
                                       SupplierOrderOptionsContext context, Map<String, String> chosen, Model model) {
        try {
            List<SupplierOrderOption> options = supplierPurchaseService.orderOptions(storeId, provider, context);
            Map<String, String> selected = new LinkedHashMap<>();
            options.forEach(option -> {
                String value = chosen == null ? null : chosen.get(option.key());
                if (StringUtils.isBlank(value)) {
                    value = option.defaultValue();
                }
                if (value != null) {
                    selected.put(option.key(), value);
                }
            });
            model.addAttribute("orderOptions", options);
            model.addAttribute("selectedOptions", selected);
        } catch (Exception e) {
            model.addAttribute("orderOptions", List.of());
            model.addAttribute("selectedOptions", Map.of());
            model.addAttribute("orderOptionsError", e.getMessage());
        }
    }
}
