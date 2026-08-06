package pl.commercelink.taxonomy;

import pl.commercelink.starter.csv.CSVReady;
import pl.commercelink.starter.csv.CSVWriter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

class TaxonomyParser {

    static final String[] COLUMNS = {
            "ean", "mfn", "brand", "name", "category", "category_id",
            "data_accuracy_score", "net_weight_g", "gross_weight_g"
    };

    private static final char NAME_REJOIN_SEPARATOR = ';';
    private static final int TRAILING_FIELD_COUNT = 5;

    static Taxonomy fromCsvRow(String[] row) {
        String ean = row[0];
        String mfn = row[1];
        String brand = row[2];
        // category, category_id, score, net, gross always sit at the end of the row;
        // an unescaped separator inside "name" (e.g. a mangled &#39; entity) only ever
        // splits the name field itself, so anchoring the tail fields from the end and
        // rejoining everything before them survives that kind of source-data corruption.
        int nameEnd = row.length - TRAILING_FIELD_COUNT;
        String name = String.join(String.valueOf(NAME_REJOIN_SEPARATOR), Arrays.copyOfRange(row, 3, nameEnd));

        String category = valueOrNull(row[nameEnd]);
        String categoryId = valueOrNull(row[nameEnd + 1]);
        int dataAccuracyScore = parseScore(row[nameEnd + 2]);
        Integer netWeight = parseWeight(row[nameEnd + 3]);
        Integer grossWeight = parseWeight(row[nameEnd + 4]);

        return new Taxonomy(ean, mfn, brand, name, category, dataAccuracyScore,
                netWeight, grossWeight, null, categoryId);
    }

    private static String valueOrNull(String value) {
        return value == null || value.isBlank() ? null : value;
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
