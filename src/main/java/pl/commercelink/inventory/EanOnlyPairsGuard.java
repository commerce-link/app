package pl.commercelink.inventory;

import pl.commercelink.inventory.supplier.InventoryItem;

/** Pairs flagged in PIM, global and store ones: an offer under such a pair joins by EAN alone. */
final class EanOnlyPairsGuard implements CodeJoinGuard {

    private final VariantCodes pairs;

    EanOnlyPairsGuard(VariantCodes pairs) {
        this.pairs = pairs;
    }

    @Override
    public boolean joinsOnlyByEan(InventoryItem offer) {
        // checked before the brand key is built, so a pool without flagged pairs does no extra work per offer
        return pairs.size() > 0 && pairs.contains(offer.brandKey(), offer.mfn());
    }

    @Override
    public VariantCodes variantCodes() {
        return pairs;
    }
}
