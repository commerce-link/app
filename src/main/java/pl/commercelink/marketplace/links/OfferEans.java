package pl.commercelink.marketplace.links;

import org.apache.commons.lang3.StringUtils;

import static pl.commercelink.taxonomy.UnifiedProductIdentifiers.unifyEan;

/**
 * The one spelling of an EAN that offer links, order lines and the offer export compare. {@code unifyEan} keeps
 * surrounding whitespace (and then cannot strip the leading zeros behind it), and a catalog or marketplace EAN may carry
 * some; if the paths trimmed differently, an order would find a product the export does not skip.
 */
public final class OfferEans {

    private OfferEans() {
    }

    /** @return the unified EAN, or null for a missing or blank one */
    public static String unify(String ean) {
        String trimmed = StringUtils.trimToNull(ean);
        return trimmed == null ? null : StringUtils.trimToNull(unifyEan(trimmed));
    }
}
