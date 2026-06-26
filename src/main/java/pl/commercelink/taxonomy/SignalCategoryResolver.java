package pl.commercelink.taxonomy;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Resolves loose supplier signals into a categoryKey deterministically, in the APP (never in PIM, pcp:463).
 * This is the main, non-LLM mechanism: a vendor-category signal maps to a categoryKey by fixed rules.
 * An unmapped value is left unresolved — there is no guessing here (that is the cold-path LLM tail, F5).
 * Language-limited to PL/EN until per-language signal normalization (Z8).
 */
@Component
public class SignalCategoryResolver {

    public static final String VENDOR_CATEGORY = "VENDOR_CATEGORY:";

    private static final Map<String, String> VENDOR_SYNONYMS = Map.ofEntries(
            Map.entry("obudowa", "Case"),
            Map.entry("obudowy", "Case"),
            Map.entry("obudowa komputerowa", "Case"),
            Map.entry("pc case", "Case"),
            Map.entry("chłodzenie", "Cooler"),
            Map.entry("chlodzenie", "Cooler"),
            Map.entry("chłodzenie procesora", "Cooler"),
            Map.entry("cooling", "Cooler"),
            Map.entry("cpu cooler", "Cooler"),
            Map.entry("wentylator", "Fan"),
            Map.entry("wentylatory", "Fan"),
            Map.entry("case fan", "Fan"),
            Map.entry("procesor", "CPU"),
            Map.entry("processor", "CPU"),
            Map.entry("karta graficzna", "GPU"),
            Map.entry("karty graficzne", "GPU"),
            Map.entry("graphics card", "GPU"),
            Map.entry("płyta główna", "Motherboard"),
            Map.entry("plyta glowna", "Motherboard"),
            Map.entry("mainboard", "Motherboard"),
            Map.entry("zasilacz", "PSU"),
            Map.entry("power supply", "PSU"),
            Map.entry("pamięć", "Memory"),
            Map.entry("pamiec", "Memory"),
            Map.entry("dysk", "Storage"),
            Map.entry("oprogramowanie", "Software"),
            Map.entry("laptop", "Laptops"),
            Map.entry("monitor", "Displays")
    );

    public Optional<String> resolve(List<String> signals) {
        if (signals == null) {
            return Optional.empty();
        }

        for (String signal : signals) {
            Optional<String> resolved = resolveSignal(signal);
            if (resolved.isPresent()) {
                return resolved;
            }
        }

        return Optional.empty();
    }

    private Optional<String> resolveSignal(String signal) {
        if (signal == null || !signal.startsWith(VENDOR_CATEGORY)) {
            return Optional.empty();
        }

        String value = signal.substring(VENDOR_CATEGORY.length()).trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty()) {
            return Optional.empty();
        }

        String synonym = VENDOR_SYNONYMS.get(value);
        if (synonym != null) {
            return Optional.of(synonym);
        }

        return matchingCategoryKey(value);
    }

    private Optional<String> matchingCategoryKey(String value) {
        for (String key : CategoryCatalog.orderedKeys()) {
            if (!key.equals(CategoryCatalog.defaultKey()) && key.equalsIgnoreCase(value)) {
                return Optional.of(key);
            }
        }
        return Optional.empty();
    }
}
