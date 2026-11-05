package pl.commercelink.taxonomy;

import pl.commercelink.inventory.supplier.SupplierProduct;

import java.util.Comparator;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Stream;

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
        return hasCategory()
                && ean != null && !ean.isEmpty()
                && mfn != null && !mfn.isEmpty()
                && brand != null && !brand.isEmpty()
                && name != null && !name.isEmpty();
    }

    public boolean hasCategory() {
        return category != null && !category.isBlank();
    }

    public boolean isPreferredOver(Taxonomy other) {
        if (hasCategory() != other.hasCategory()) {
            return hasCategory();
        }
        return dataAccuracyScore < other.dataAccuracyScore;
    }

    public Taxonomy mergedWith(Taxonomy incoming) {
        Taxonomy winner = winnerAgainst(incoming);
        Integer net = weightOfBetterSource(incoming, Taxonomy::netWeightInGrams);
        Integer gross = weightOfBetterSource(incoming, Taxonomy::grossWeightInGrams);
        if (Objects.equals(net, winner.netWeightInGrams) && Objects.equals(gross, winner.grossWeightInGrams)) {
            return winner;
        }
        return winner.withWeights(net, gross);
    }

    private Taxonomy winnerAgainst(Taxonomy incoming) {
        if (hasCategory() != incoming.hasCategory()) {
            return hasCategory() ? this : incoming;
        }
        return incoming.dataAccuracyScore <= dataAccuracyScore ? incoming : this;
    }

    private Integer weightOfBetterSource(Taxonomy incoming, Function<Taxonomy, Integer> weight) {
        return Stream.of(incoming, this)
                .filter(t -> weight.apply(t) != null)
                .min(Comparator.comparingInt(Taxonomy::dataAccuracyScore))
                .map(weight)
                .orElse(null);
    }

    public Taxonomy withCategory(String category, String categoryId) {
        return new Taxonomy(ean, mfn, brand, name, category, dataAccuracyScore,
                netWeightInGrams, grossWeightInGrams, rawCategory, categoryId);
    }

    public Taxonomy withWeights(Integer netWeightInGrams, Integer grossWeightInGrams) {
        return new Taxonomy(ean, mfn, brand, name, category, dataAccuracyScore,
                netWeightInGrams, grossWeightInGrams, rawCategory, categoryId);
    }
}
