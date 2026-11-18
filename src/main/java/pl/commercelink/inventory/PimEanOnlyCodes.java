package pl.commercelink.inventory;

import pl.commercelink.inventory.supplier.InventoryItem;
import pl.commercelink.pim.api.PimEntry;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Brand and manufacturer code pairs of the global PIM entries an operator flagged to be joined by EAN only, keyed
 * exactly as an offer with that brand and code is, so they can be looked up with the offer's own values.
 */
final class PimEanOnlyCodes {

    private PimEanOnlyCodes() {
    }

    static VariantCodes of(Collection<PimEntry> entries) {
        Set<VariantCodes.BrandCode> pairs = new HashSet<>();
        for (PimEntry entry : entries) {
            // the approval does not matter: the flag is the operator's decision about the codes, not the entry
            if (!entry.global() || !entry.eanOnlyMatching() || !InventoryItem.isMatchableBrand(entry.brand())) {
                continue;
            }
            String brandKey = InventoryItem.brandKeyOf(entry.brand());
            for (String mpn : entry.mpns()) {
                String code = InventoryItem.codeOf(mpn);
                if (code != null && !code.isBlank()) {
                    pairs.add(new VariantCodes.BrandCode(brandKey, code));
                }
            }
        }
        return VariantCodes.of(pairs);
    }
}
