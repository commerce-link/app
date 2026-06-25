package pl.commercelink.inventory.supplier;

import pl.commercelink.inventory.supplier.api.Taxonomy;

final class StoreFeedTaxonomy {

    private StoreFeedTaxonomy() {
    }

    static Taxonomy deprioritized(Taxonomy taxonomy, int penalty) {
        if (penalty <= 0) {
            return taxonomy;
        }
        return new Taxonomy(taxonomy.ean(), taxonomy.mfn(), taxonomy.brand(), taxonomy.name(),
                taxonomy.category(), taxonomy.dataAccuracyScore() + penalty,
                taxonomy.netWeightInGrams(), taxonomy.grossWeightInGrams(), taxonomy.categoryKey(), taxonomy.signals());
    }
}
