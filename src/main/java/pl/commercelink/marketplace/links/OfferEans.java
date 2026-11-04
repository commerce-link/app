package pl.commercelink.marketplace.links;

import org.apache.commons.lang3.StringUtils;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import static pl.commercelink.taxonomy.UnifiedProductIdentifiers.unifyEan;

/**
 * The one spelling of an EAN that offer links, order lines and the offer export compare. {@code unifyEan} keeps
 * surrounding whitespace (and then cannot strip the leading zeros behind it), and a catalog or marketplace EAN may carry
 * some; if the paths trimmed differently, an order would find a product the export does not skip.
 */
public final class OfferEans {

    /**
     * What counts as an EAN once unified: 8 to 14 ASCII digits (EAN-8 up to GTIN-14). The check digit is not checked
     * here, as a marketplace's code is taken as the marketplace gives it; a code an operator types is checked on top.
     */
    public static final Pattern VALID_UNIFIED = Pattern.compile("[0-9]{8,14}");

    private OfferEans() {
    }

    /** @return the unified EAN, or null for a missing or blank one */
    public static String unify(String ean) {
        String trimmed = StringUtils.trimToNull(ean);
        return trimmed == null ? null : StringUtils.trimToNull(unifyEan(trimmed));
    }

    /**
     * Whether a unified EAN ({@link #unify}) has the format of one. Anything else ({@code N/A}, {@code 123:456}, a code
     * with a letter, a {@code #} or a space inside) would end up in an order item's sku, where ":" separates codes and a
     * leading "#" marks a set.
     */
    public static boolean isValid(String unified) {
        return unified != null && VALID_UNIFIED.matcher(unified).matches();
    }

    /** The EAN unified, or null when it has not the {@linkplain #isValid format} of one. */
    public static String unifyValid(String ean) {
        String unified = unify(ean);
        return isValid(unified) ? unified : null;
    }

    /** The EANs unified, without the ones that are not {@linkplain #isValid valid}, each once, in their order. */
    public static List<String> validUnified(Collection<String> eans) {
        if (eans == null) {
            return List.of();
        }
        Set<String> valid = new LinkedHashSet<>();
        for (String ean : eans) {
            String unified = unify(ean);
            if (isValid(unified)) {
                valid.add(unified);
            }
        }
        return List.copyOf(valid);
    }
}
