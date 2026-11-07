package pl.commercelink.products;

import org.apache.commons.lang3.StringUtils;

public record ProductData(String name, String brand, String category, String ean, String mfn,
                          Integer netWeightInGrams, Integer grossWeightInGrams) {

    public static final ProductData EMPTY = new ProductData(null, null, null, null, null, null, null);

    public ProductData orElse(ProductData fallback) {
        return new ProductData(
                StringUtils.isNotBlank(name) ? name : fallback.name,
                StringUtils.isNotBlank(brand) ? brand : fallback.brand,
                StringUtils.isNotBlank(category) ? category : fallback.category,
                StringUtils.isNotBlank(ean) ? ean : fallback.ean,
                StringUtils.isNotBlank(mfn) ? mfn : fallback.mfn,
                netWeightInGrams != null ? netWeightInGrams : fallback.netWeightInGrams,
                grossWeightInGrams != null ? grossWeightInGrams : fallback.grossWeightInGrams);
    }
}
