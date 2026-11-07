package pl.commercelink.warehouse.builtin;

import pl.commercelink.pim.api.PimEntry;
import pl.commercelink.pim.api.PimView;
import pl.commercelink.taxonomy.Taxonomy;
import pl.commercelink.taxonomy.TaxonomyCache;

record ReportedProduct(String category, String brand, Integer netWeightInGrams, Integer grossWeightInGrams) {

    private static final ReportedProduct UNKNOWN = new ReportedProduct(null, null, null, null);

    static ReportedProduct of(String ean, TaxonomyCache taxonomyCache, PimView pim) {
        Taxonomy record = taxonomyCache.findByEan(ean);
        if (record != null) {
            return new ReportedProduct(record.category(), record.brand(),
                    record.netWeightInGrams(), record.grossWeightInGrams());
        }
        return pim.findByGtin(ean)
                .map(ReportedProduct::of)
                .orElse(UNKNOWN);
    }

    private static ReportedProduct of(PimEntry entry) {
        return new ReportedProduct(entry.category(), entry.brand(),
                entry.netWeightInGrams(), entry.grossWeightInGrams());
    }
}
