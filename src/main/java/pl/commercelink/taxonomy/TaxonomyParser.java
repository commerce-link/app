package pl.commercelink.taxonomy;

import pl.commercelink.inventory.supplier.api.Taxonomy;
import pl.commercelink.starter.csv.CSVReady;
import pl.commercelink.starter.csv.CSVWriter;

import java.io.IOException;
import java.util.Collection;
import java.util.List;

class TaxonomyParser {

    static final String[] COLUMNS = {
            "ean", "mfn", "brand", "name", "category", "data_accuracy_score",
            "net_weight_g", "gross_weight_g", "category_key", "signals"
    };

    // Unit Separator: separates the signal list inside the single CSV cell; never appears in vendor text.
    private static final String SIGNAL_DELIMITER = String.valueOf((char) 0x1F);

    static Taxonomy fromCsvRow(String[] row) {
        String ean = row[0];
        String mfn = row[1];
        String brand = row[2];
        String name = row[3];
        ProductCategory category = parseCategory(row[4]);
        int dataAccuracyScore = parseScore(row[5]);
        Integer netWeight = row.length > 6 ? parseWeight(row[6]) : null;
        Integer grossWeight = row.length > 7 ? parseWeight(row[7]) : null;
        String categoryKey = row.length > 8 ? row[8] : category.name();
        List<String> signals = row.length > 9 ? decodeSignals(row[9]) : List.of();
        return new Taxonomy(ean, mfn, brand, name, category, dataAccuracyScore, netWeight, grossWeight, categoryKey, signals);
    }

    static byte[] toCsv(Collection<Taxonomy> taxonomies) {
        try {
            List<CSVReady> rows = taxonomies.stream()
                    .<CSVReady>map(t -> () -> toStringArray(t))
                    .toList();
            return new CSVWriter().writeAllRowsToBytes(rows, COLUMNS);
        } catch (IOException e) {
            throw new RuntimeException("Failed to generate CSV", e);
        }
    }

    private static ProductCategory parseCategory(String value) {
        try {
            return ProductCategory.valueOf(value);
        } catch (IllegalArgumentException e) {
            return ProductCategory.Other;
        }
    }

    private static int parseScore(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return Integer.MAX_VALUE;
        }
    }

    private static Integer parseWeight(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            int weight = Integer.parseInt(value.trim());
            return weight > 0 ? weight : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String encodeSignals(List<String> signals) {
        return (signals == null || signals.isEmpty()) ? "" : String.join(SIGNAL_DELIMITER, signals);
    }

    private static List<String> decodeSignals(String cell) {
        if (cell == null || cell.isBlank()) {
            return List.of();
        }
        return List.of(cell.split(SIGNAL_DELIMITER));
    }

    private static String[] toStringArray(Taxonomy t) {
        return new String[]{
                t.ean() != null ? t.ean() : "",
                t.mfn() != null ? t.mfn() : "",
                t.brand() != null ? t.brand() : "",
                t.name() != null ? t.name() : "",
                t.category() != null ? t.category().name() : "",
                String.valueOf(t.dataAccuracyScore()),
                t.netWeightInGrams() != null ? t.netWeightInGrams().toString() : "",
                t.grossWeightInGrams() != null ? t.grossWeightInGrams().toString() : "",
                t.categoryKey() != null ? t.categoryKey() : "",
                encodeSignals(t.signals())
        };
    }
}
