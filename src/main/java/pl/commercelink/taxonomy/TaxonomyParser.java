package pl.commercelink.taxonomy;

import pl.commercelink.taxonomy.BrandMapper;
import pl.commercelink.inventory.supplier.api.Taxonomy;
import pl.commercelink.starter.csv.CSVReady;
import pl.commercelink.starter.csv.CSVWriter;

import java.io.IOException;
import java.util.Collection;
import java.util.List;

class TaxonomyParser {

    static final String[] COLUMNS = {
            "ean", "mfn", "brand", "name", "category", "data_accuracy_score"
    };

    static Taxonomy fromCsvRow(String[] row) {
        String ean = row[0];
        String mfn = row[1];
        String brand = BrandMapper.unifyBrand(row[2]);
        String name = row[3];
        ProductCategory category = parseCategory(row[4]);
        int dataAccuracyScore = parseScore(row[5]);
        return new Taxonomy(ean, mfn, brand, name, category, dataAccuracyScore);
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

    private static String[] toStringArray(Taxonomy t) {
        return new String[]{
                t.ean() != null ? t.ean() : "",
                t.mfn() != null ? t.mfn() : "",
                t.brand() != null ? t.brand() : "",
                t.name() != null ? t.name() : "",
                t.category() != null ? t.category().name() : "",
                String.valueOf(t.dataAccuracyScore())
        };
    }
}
