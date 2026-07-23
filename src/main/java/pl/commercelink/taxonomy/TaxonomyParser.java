package pl.commercelink.taxonomy;

import pl.commercelink.inventory.supplier.api.Taxonomy;
import pl.commercelink.starter.csv.CSVReady;
import pl.commercelink.starter.csv.CSVWriter;

import java.io.IOException;
import java.util.Collection;
import java.util.List;

class TaxonomyParser {

    static final String[] COLUMNS = {
            "ean", "mfn", "brand", "name", "category", "category_id",
            "data_accuracy_score", "net_weight_g", "gross_weight_g"
    };

    static Taxonomy fromCsvRow(String[] row) {
        String ean = row[0];
        String mfn = row[1];
        String brand = row[2];
        String name = row[3];
        String category = row[4] == null || row[4].isBlank() ? null : row[4];
        boolean newFormat = row.length > 8;
        String categoryId = newFormat && row[5] != null && !row[5].isBlank() ? row[5] : null;
        int scoreIndex = newFormat ? 6 : 5;
        int netIndex = newFormat ? 7 : 6;
        int grossIndex = newFormat ? 8 : 7;
        int dataAccuracyScore = parseScore(row[scoreIndex]);
        Integer netWeight = row.length > netIndex ? parseWeight(row[netIndex]) : null;
        Integer grossWeight = row.length > grossIndex ? parseWeight(row[grossIndex]) : null;
        return new Taxonomy(ean, mfn, brand, name, category, dataAccuracyScore,
                netWeight, grossWeight, null, categoryId);
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

    private static String[] toStringArray(Taxonomy t) {
        return new String[]{
                t.ean() != null ? t.ean() : "",
                t.mfn() != null ? t.mfn() : "",
                t.brand() != null ? t.brand() : "",
                t.name() != null ? t.name() : "",
                t.category() != null ? t.category() : "",
                t.categoryId() != null ? t.categoryId() : "",
                String.valueOf(t.dataAccuracyScore()),
                t.netWeightInGrams() != null ? t.netWeightInGrams().toString() : "",
                t.grossWeightInGrams() != null ? t.grossWeightInGrams().toString() : ""
        };
    }
}
