package pl.commercelink.inventory;

import pl.commercelink.inventory.supplier.InventoryItem;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Manufacturer codes a supplier gives to several EANs of one brand. Such a code names a model (one code for every
 * size), the brand itself or a customs heading rather than a product, so offers under it join others only by EAN.
 * A code is shared per supplier: the same code at another supplier, with one EAN, still joins as before.
 */
public final class SharedManufacturerCodes {

    private final Set<InventoryItem> offersUnderSharedCode;

    private SharedManufacturerCodes(Set<InventoryItem> offersUnderSharedCode) {
        this.offersUnderSharedCode = offersUnderSharedCode;
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
        return new SharedManufacturerCodes(offersUnderSharedCode);
    }

    public boolean isShared(InventoryItem offer) {
        return offersUnderSharedCode.contains(offer);
    }

    public Set<InventoryItem> offersUnderSharedCode() {
        return Collections.unmodifiableSet(offersUnderSharedCode);
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
            String brand = offer.brand() == null ? null : offer.brand().trim().toLowerCase(Locale.ROOT);
            return new SupplierCode(offer.supplier(), offer.mfn(), brand);
        }
    }
}
