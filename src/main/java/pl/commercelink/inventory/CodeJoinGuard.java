package pl.commercelink.inventory;

import pl.commercelink.inventory.supplier.InventoryItem;

import java.util.List;

/** A rule that keeps manufacturer codes from joining offers, and names the pairs that refuse an own-to-global link. */
interface CodeJoinGuard {

    /** Whether the offer joins others by EAN alone: its code neither pulls offers in nor is pulled by them. */
    boolean joinsOnlyByEan(InventoryItem offer);

    /** The brand and code pairs that refuse a link between an own and a global group. */
    VariantCodes variantCodes();

    /** A guard that leaves grouping alone and only refuses links: the pairs a store inherits from the global pool. */
    static CodeJoinGuard linkOnly(VariantCodes pairs) {
        return new CodeJoinGuard() {
            @Override
            public boolean joinsOnlyByEan(InventoryItem offer) {
                return false;
            }

            @Override
            public VariantCodes variantCodes() {
                return pairs;
            }
        };
    }

    /** Offers are EAN-only when any guard says so; the pairs are united in the given order. */
    static CodeJoinGuard anyOf(List<CodeJoinGuard> guards) {
        List<CodeJoinGuard> ordered = List.copyOf(guards);
        return new CodeJoinGuard() {
            @Override
            public boolean joinsOnlyByEan(InventoryItem offer) {
                for (CodeJoinGuard guard : ordered) {
                    if (guard.joinsOnlyByEan(offer)) {
                        return true;
                    }
                }
                return false;
            }

            @Override
            public VariantCodes variantCodes() {
                VariantCodes united = VariantCodes.none();
                for (CodeJoinGuard guard : ordered) {
                    united = united.plus(guard.variantCodes());
                }
                return united;
            }
        };
    }
}
