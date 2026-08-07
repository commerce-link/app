package pl.commercelink.taxonomy;

import pl.commercelink.inventory.supplier.api.SupplierProduct;

public record Taxonomy(String ean, String mfn, String brand, String name,
                       String category, int dataAccuracyScore,
                       Integer netWeightInGrams, Integer grossWeightInGrams,
                       String rawCategory, String categoryId) {

    public static final String SERVICES = "Services";

    public static final Taxonomy EMPTY = new Taxonomy("N/A", "N/A", "N/A", "N/A",
            null, Integer.MAX_VALUE, null, null);

    public Taxonomy(String ean, String mfn, String brand, String name, String category,
                    int dataAccuracyScore, Integer netWeightInGrams, Integer grossWeightInGrams,
                    String rawCategory) {
        this(ean, mfn, brand, name, category, dataAccuracyScore,
                netWeightInGrams, grossWeightInGrams, rawCategory, null);
    }

    public Taxonomy(String ean, String mfn, String brand, String name, String category,
                    int dataAccuracyScore, Integer netWeightInGrams, Integer grossWeightInGrams) {
        this(ean, mfn, brand, name, category, dataAccuracyScore,
                netWeightInGrams, grossWeightInGrams, null, null);
    }

    public static Taxonomy from(SupplierProduct product) {
        return new Taxonomy(product.ean(), product.mfn(), product.brand(), product.name(),
                null, product.dataAccuracyScore(),
                product.netWeightInGrams(), product.grossWeightInGrams(),
                product.rawCategory(), null);
    }

    public boolean isProcessable() {
        return category != null && !category.isBlank()
                && ean != null && !ean.isEmpty()
                && mfn != null && !mfn.isEmpty()
                && brand != null && !brand.isEmpty()
                && name != null && !name.isEmpty();
    }
}
