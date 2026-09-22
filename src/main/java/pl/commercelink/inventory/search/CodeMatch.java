package pl.commercelink.inventory.search;

import pl.commercelink.taxonomy.UnifiedProductIdentifiers;

import java.util.Objects;

public enum CodeMatch {
    SAME,
    EAN_DIFFERS,
    CODE_DIFFERS,
    BOTH_DIFFER;

    static CodeMatch of(ProductCodes product, String ean, String code) {
        boolean eanDiffers = differs(product.unifiedEan(), UnifiedProductIdentifiers.unifyEan(blankToNull(ean)));
        boolean codeDiffers = differs(product.unifiedCode(), UnifiedProductIdentifiers.unifyMfn(blankToNull(code)));
        if (eanDiffers && codeDiffers) {
            return BOTH_DIFFER;
        }
        return eanDiffers ? EAN_DIFFERS : codeDiffers ? CODE_DIFFERS : SAME;
    }

    // a code missing on either side proves nothing, so only two known, different values count as a mismatch
    private static boolean differs(String productValue, String rowValue) {
        return productValue != null && rowValue != null && !Objects.equals(productValue, rowValue);
    }

    static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    public boolean isInfo() {
        return this == EAN_DIFFERS || this == CODE_DIFFERS;
    }

    public boolean isWarning() {
        return this == BOTH_DIFFER;
    }
}
