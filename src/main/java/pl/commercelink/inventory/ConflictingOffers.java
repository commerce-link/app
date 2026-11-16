package pl.commercelink.inventory;

import pl.commercelink.inventory.supplier.InventoryItem;
import pl.commercelink.inventory.supplier.SupplierIdentity;
import pl.commercelink.products.brand.NoNameBrand;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Offers whose EAN points to one product and whose brand and manufacturer code point to another, each side
 * confirmed by at least {@link #MIN_CONFIRMATIONS} other suppliers. Such an offer is a data error of its supplier
 * and joining it by either key would merge two products, so it is left out of every group.
 * Witnesses (offers of another scope) only confirm; they are never judged.
 */
public final class ConflictingOffers {

    static final int MIN_CONFIRMATIONS = 2;

    private static final ConflictingOffers NONE = new ConflictingOffers(Collections.emptySet());

    private final Set<InventoryItem> conflicting;

    private ConflictingOffers(Set<InventoryItem> conflicting) {
        this.conflicting = conflicting;
    }

    public static ConflictingOffers none() {
        return NONE;
    }

    public static ConflictingOffers of(Collection<InventoryItem> offers, Collection<InventoryItem> witnesses,
                                       SharedManufacturerCodes sharedCodes) {
        Index index = new Index();
        offers.forEach(index::add);
        witnesses.forEach(index::add);

        // by identity, like SharedManufacturerCodes: callers ask about the same instances
        Set<InventoryItem> conflicting = Collections.newSetFromMap(new IdentityHashMap<>());
        for (InventoryItem offer : offers) {
            if (isJudged(offer, sharedCodes) && index.isConflicting(offer)) {
                conflicting.add(offer);
            }
        }
        return conflicting.isEmpty() ? NONE : new ConflictingOffers(conflicting);
    }

    public boolean contains(InventoryItem offer) {
        return conflicting.contains(offer);
    }

    public int size() {
        return conflicting.size();
    }

    private static boolean isJudged(InventoryItem offer, SharedManufacturerCodes sharedCodes) {
        return isUsableBrand(offer.brand()) && !sharedCodes.isShared(offer);
    }

    private static boolean isUsableBrand(String brand) {
        return brand != null && !brand.isBlank() && !NoNameBrand.is(brand);
    }

    private static String brandKey(String brand) {
        return brand.trim().toLowerCase(Locale.ROOT);
    }

    // A manual feed is a supplier of its own, any other identity counts as its adapter type, so one supplier
    // connected both globally and as the store's own is a single confirmation.
    private static String supplierOf(InventoryItem offer) {
        String supplier = offer.supplier();
        return SupplierIdentity.isManual(supplier) ? supplier : SupplierIdentity.typeOf(supplier);
    }

    private record Triple(String ean, String brand, String code) {
    }

    private record BrandCode(String brand, String code) {
    }

    private static final class Index {

        private final Map<Triple, Set<String>> suppliersByTriple = new HashMap<>();
        private final Map<String, Set<BrandCode>> brandCodesByEan = new HashMap<>();
        private final Map<BrandCode, Set<String>> eansByBrandCode = new HashMap<>();

        void add(InventoryItem offer) {
            if (!isUsableBrand(offer.brand())) {
                return;
            }
            BrandCode brandCode = new BrandCode(brandKey(offer.brand()), offer.mfn());
            suppliersByTriple.computeIfAbsent(new Triple(offer.ean(), brandCode.brand(), brandCode.code()), k -> new HashSet<>())
                    .add(supplierOf(offer));
            brandCodesByEan.computeIfAbsent(offer.ean(), k -> new HashSet<>()).add(brandCode);
            eansByBrandCode.computeIfAbsent(brandCode, k -> new HashSet<>()).add(offer.ean());
        }

        boolean isConflicting(InventoryItem offer) {
            String own = supplierOf(offer);
            String ean = offer.ean();
            BrandCode brandCode = new BrandCode(brandKey(offer.brand()), offer.mfn());

            if (holdersOtherThan(new Triple(ean, brandCode.brand(), brandCode.code()), own) > 0) {
                return false;
            }
            Set<String> otherEans = confirmedEansOf(brandCode, ean, own);
            if (otherEans.isEmpty()) {
                return false;
            }
            Set<BrandCode> otherCodes = confirmedCodesOf(ean, brandCode, own);
            if (otherCodes.isEmpty()) {
                return false;
            }
            for (String otherEan : otherEans) {
                for (BrandCode otherCode : otherCodes) {
                    if (suppliersByTriple.containsKey(new Triple(otherEan, otherCode.brand(), otherCode.code()))) {
                        return false;
                    }
                }
            }
            return true;
        }

        private Set<String> confirmedEansOf(BrandCode brandCode, String ownEan, String own) {
            Set<String> confirmed = new HashSet<>();
            for (String candidate : eansByBrandCode.getOrDefault(brandCode, Set.of())) {
                if (!candidate.equals(ownEan)
                        && holdersOtherThan(new Triple(candidate, brandCode.brand(), brandCode.code()), own) >= MIN_CONFIRMATIONS) {
                    confirmed.add(candidate);
                }
            }
            return confirmed;
        }

        private Set<BrandCode> confirmedCodesOf(String ean, BrandCode own, String ownSupplier) {
            Set<BrandCode> confirmed = new HashSet<>();
            for (BrandCode candidate : brandCodesByEan.getOrDefault(ean, Set.of())) {
                if (!candidate.code().equals(own.code()) && !CodeNotation.sameNotation(own.code(), candidate.code())
                        && holdersOtherThan(new Triple(ean, candidate.brand(), candidate.code()), ownSupplier) >= MIN_CONFIRMATIONS) {
                    confirmed.add(candidate);
                }
            }
            return confirmed;
        }

        private int holdersOtherThan(Triple triple, String supplier) {
            Set<String> holders = suppliersByTriple.get(triple);
            if (holders == null) {
                return 0;
            }
            return holders.contains(supplier) ? holders.size() - 1 : holders.size();
        }
    }
}
