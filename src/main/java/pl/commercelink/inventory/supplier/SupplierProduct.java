package pl.commercelink.inventory.supplier;

import pl.commercelink.taxonomy.UnifiedProductIdentifiers;

public record SupplierProduct(String ean, String mfn, String brand, String name,
                              int dataAccuracyScore,
                              Integer netWeightInGrams, Integer grossWeightInGrams,
                              String rawCategory) {

    public static final SupplierProduct EMPTY = new SupplierProduct("N/A", "N/A", "N/A", "N/A",
            Integer.MAX_VALUE, null, null, null);

    public SupplierProduct(String ean, String mfn, String brand, String name,
                           int dataAccuracyScore, Integer netWeightInGrams, Integer grossWeightInGrams) {
        this(ean, mfn, brand, name, dataAccuracyScore, netWeightInGrams, grossWeightInGrams, null);
    }

    public SupplierProduct {
        ean = UnifiedProductIdentifiers.unifyEan(ean);
        mfn = UnifiedProductIdentifiers.unifyMfn(mfn);
    }
}
