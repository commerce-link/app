package pl.commercelink.inventory.supplier;

import pl.commercelink.inventory.CodeNotation;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * EANs one supplier puts on several different products in a feed (a "0000000000000"-style filler). Such a number
 * names no product, so an offer under it must not join others by EAN.
 */
final class PlaceholderEans {

    static final int MIN_CODES = 3;

    private static final PlaceholderEans NONE = new PlaceholderEans(Set.of());

    private final Set<String> eans;

    private PlaceholderEans(Set<String> eans) {
        this.eans = eans;
    }

    static PlaceholderEans none() {
        return NONE;
    }

    // Rows without stock or in a category still count: the number is shared whatever happens to the offer later.
    static PlaceholderEans of(Collection<InventoryItem> feedRows) {
        Map<String, Set<String>> codesByEan = new HashMap<>();
        for (InventoryItem row : feedRows) {
            if (row.hasEan() && row.hasMfn()) {
                codesByEan.computeIfAbsent(row.ean(), ean -> new HashSet<>()).add(CodeNotation.normalize(row.mfn()));
            }
        }
        Set<String> placeholders = new HashSet<>();
        codesByEan.forEach((ean, codes) -> {
            if (codes.size() >= MIN_CODES) {
                placeholders.add(ean);
            }
        });
        return placeholders.isEmpty() ? NONE : new PlaceholderEans(placeholders);
    }

    boolean contains(String ean) {
        return ean != null && eans.contains(ean);
    }

    int size() {
        return eans.size();
    }
}
