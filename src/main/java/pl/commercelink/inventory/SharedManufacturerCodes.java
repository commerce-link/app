package pl.commercelink.inventory;

import pl.commercelink.inventory.supplier.InventoryItem;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Manufacturer codes a supplier gives to several EANs of one brand. Such a code names a model (one code for every
 * size), the brand itself or a customs heading rather than a product, so offers under it join others only by EAN.
 * A code is shared per supplier: for {@link #isShared} the same code at another supplier, with one EAN, still joins
 * as before. The same pairs without the supplier, {@link #variantCodes}, are used to refuse a link between an own
 * and a global group.
 */
public final class SharedManufacturerCodes {

    private final Set<InventoryItem> offersUnderSharedCode;
    private final VariantCodes variantCodes;

    private SharedManufacturerCodes(Set<InventoryItem> offersUnderSharedCode, VariantCodes variantCodes) {
        this.offersUnderSharedCode = offersUnderSharedCode;
        this.variantCodes = variantCodes;
    }

    public static SharedManufacturerCodes of(Collection<InventoryItem> offers) {
        Set<SupplierCode> sharedCodes = sharedCodes(offers);

        // by identity: callers ask about the same instances, and a record would compare all of its fields
        Set<InventoryItem> offersUnderSharedCode = Collections.newSetFromMap(new IdentityHashMap<>());
        if (!sharedCodes.isEmpty()) {
            for (InventoryItem offer : offers) {
                if (sharedCodes.contains(SupplierCode.of(offer))) {
                    offersUnderSharedCode.add(offer);
                }
            }
        }
        return new SharedManufacturerCodes(offersUnderSharedCode, variantCodes(sharedCodes));
    }

    public boolean isShared(InventoryItem offer) {
        return offersUnderSharedCode.contains(offer);
    }

    public Set<InventoryItem> offersUnderSharedCode() {
        return Collections.unmodifiableSet(offersUnderSharedCode);
    }

    /** The brand and code of every per-supplier shared code, without the supplier. */
    public VariantCodes variantCodes() {
        return variantCodes;
    }

    private static VariantCodes variantCodes(Set<SupplierCode> sharedCodes) {
        Set<VariantCodes.BrandCode> pairs = new HashSet<>();
        for (SupplierCode code : sharedCodes) {
            // a pair without a brand or a code names no product: it is never a variant code
            if (code.brand() != null && code.mfn() != null) {
                pairs.add(new VariantCodes.BrandCode(code.brand(), code.mfn()));
            }
        }
        return VariantCodes.of(pairs);
    }

    private static Set<SupplierCode> sharedCodes(Collection<InventoryItem> offers) {
        Map<SupplierCode, String> firstEanByCode = new HashMap<>();
        Set<SupplierCode> sharedCodes = new HashSet<>();
        for (InventoryItem offer : offers) {
            SupplierCode code = SupplierCode.of(offer);
            String firstEan = firstEanByCode.putIfAbsent(code, offer.ean());
            if (firstEan != null && !firstEan.equals(offer.ean())) {
                sharedCodes.add(code);
            }
        }
        return sharedCodes;
    }

    private record SupplierCode(String supplier, String mfn, String brand) {

        static SupplierCode of(InventoryItem offer) {
            return new SupplierCode(offer.supplier(), offer.mfn(), offer.brandKey());
        }
    }
}
