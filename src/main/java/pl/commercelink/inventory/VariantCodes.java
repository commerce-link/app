package pl.commercelink.inventory;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Brand and manufacturer code pairs some supplier of a pool lists under several EANs, used to refuse a link between
 * an own and a global group.
 */
public final class VariantCodes {

    private static final VariantCodes NONE = new VariantCodes(Collections.emptySet());

    private final Set<BrandCode> codes;

    private VariantCodes(Set<BrandCode> codes) {
        this.codes = codes;
    }

    public static VariantCodes none() {
        return NONE;
    }

    // the caller hands over a set it no longer modifies
    static VariantCodes of(Set<BrandCode> codes) {
        return codes.isEmpty() ? NONE : new VariantCodes(codes);
    }

    public VariantCodes plus(VariantCodes other) {
        if (other.codes.isEmpty()) {
            return this;
        }
        if (codes.isEmpty()) {
            return other;
        }
        Set<BrandCode> union = new HashSet<>(codes);
        union.addAll(other.codes);
        return new VariantCodes(union);
    }

    public boolean contains(String brandKey, String mfn) {
        // a missing brand or code names no product, so it is never a variant code
        return brandKey != null && mfn != null && !codes.isEmpty() && codes.contains(new BrandCode(brandKey, mfn));
    }

    public int size() {
        return codes.size();
    }

    /** How many pairs of this set the other one lacks. */
    public int countNotIn(VariantCodes other) {
        if (other.codes.isEmpty()) {
            return codes.size();
        }
        int missing = 0;
        for (BrandCode code : codes) {
            if (!other.codes.contains(code)) {
                missing++;
            }
        }
        return missing;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof VariantCodes that && codes.equals(that.codes);
    }

    @Override
    public int hashCode() {
        return codes.hashCode();
    }

    record BrandCode(String brandKey, String mfn) {
    }
}
