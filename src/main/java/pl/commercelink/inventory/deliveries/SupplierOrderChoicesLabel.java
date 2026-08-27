package pl.commercelink.inventory.deliveries;

import pl.commercelink.inventory.supplier.api.SupplierOrderOption;
import pl.commercelink.inventory.supplier.api.SupplierOrderOptionChoice;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class SupplierOrderChoicesLabel {

    private SupplierOrderChoicesLabel() {
    }

    public static String of(List<SupplierOrderOption> options, Map<String, String> chosen) {
        if (chosen == null || chosen.isEmpty()) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        Set<String> covered = new HashSet<>();
        for (SupplierOrderOption option : options) {
            String value = chosen.get(option.key());
            if (value == null) {
                continue;
            }
            covered.add(option.key());
            parts.add(option.label() + ": " + option.choice(value).map(SupplierOrderOptionChoice::label).orElse(value));
        }
        chosen.entrySet().stream().filter(e -> !covered.contains(e.getKey()))
                .forEach(e -> parts.add(e.getKey() + ": " + e.getValue()));
        return String.join(" · ", parts);
    }
}
