package pl.commercelink.products;

import org.apache.commons.lang3.StringUtils;

import static pl.commercelink.taxonomy.UnifiedProductIdentifiers.unifyEan;
import static pl.commercelink.taxonomy.UnifiedProductIdentifiers.unifyMfn;

public record ProductData(String name, String brand, String category, String categoryId, String ean, String mfn,
                          Integer netWeightInGrams, Integer grossWeightInGrams) {

    public static final ProductData EMPTY = new ProductData(null, null, null, null, null, null, null, null);

    public ProductData {
        ean = unifyEan(StringUtils.trimToNull(ean));
        mfn = unifyMfn(StringUtils.trimToNull(mfn));
    }

    // the category id names the category, so both come from the same source
    public ProductData orElse(ProductData fallback) {
        boolean ownCategory = StringUtils.isNotBlank(category);
        return new ProductData(
                StringUtils.isNotBlank(name) ? name : fallback.name,
                StringUtils.isNotBlank(brand) ? brand : fallback.brand,
                ownCategory ? category : fallback.category,
                ownCategory ? categoryId : fallback.categoryId,
                StringUtils.isNotBlank(ean) ? ean : fallback.ean,
                StringUtils.isNotBlank(mfn) ? mfn : fallback.mfn,
                netWeightInGrams != null ? netWeightInGrams : fallback.netWeightInGrams,
                grossWeightInGrams != null ? grossWeightInGrams : fallback.grossWeightInGrams);
    }
}
