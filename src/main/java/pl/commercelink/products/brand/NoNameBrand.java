package pl.commercelink.products.brand;

import java.util.Locale;
import java.util.Set;

public final class NoNameBrand {

    public static final String NAME = "NoName";

    private static final Set<String> PLACEHOLDERS = Set.of(
            "no name", "noname", "no-name", "oem", "inne", "inny", "brak", "sonstige", "mix",
            "unknown", "other", "n/a", "none", "-");

    private NoNameBrand() {
    }

    public static boolean is(String brand) {
        return brand != null && NAME.equalsIgnoreCase(brand.trim());
    }

    public static boolean isPlaceholder(String brand) {
        return brand != null && PLACEHOLDERS.contains(brand.trim().toLowerCase(Locale.ROOT));
    }
}
