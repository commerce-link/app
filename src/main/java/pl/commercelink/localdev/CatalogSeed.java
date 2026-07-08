package pl.commercelink.localdev;

import pl.commercelink.starter.csv.CSVLoader;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class CatalogSeed {

    public static final String RESOURCE = "/local-init/seed/catalog.csv";

    private static final int MIN_COLUMNS = 14;

    private CatalogSeed() {
    }

    public static List<CatalogSeedRow> load() {
        InputStream stream = CatalogSeed.class.getResourceAsStream(RESOURCE);
        if (stream == null) {
            throw new IllegalStateException("Missing local seed resource: " + RESOURCE);
        }
        List<CatalogSeedRow> rows = new ArrayList<>();
        try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            new CSVLoader(reader).readRows(CSVLoader.DEFAULT_SEPARATOR, fields -> {
                if (fields.length >= MIN_COLUMNS) {
                    rows.add(toRow(fields));
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read " + RESOURCE, e);
        }
        if (rows.isEmpty()) {
            throw new IllegalStateException(RESOURCE + " produced zero rows");
        }
        return rows;
    }

    private static CatalogSeedRow toRow(String[] f) {
        List<String> suppliers = Arrays.stream(f[11].split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        return new CatalogSeedRow(
                f[0].trim(), f[1].trim(), f[2].trim(), f[3].trim(), f[4].trim(), f[5].trim(), f[6].trim(),
                Integer.parseInt(f[7].trim()), Integer.parseInt(f[8].trim()),
                Integer.parseInt(f[9].trim()), Integer.parseInt(f[10].trim()),
                suppliers, Boolean.parseBoolean(f[12].trim()), Boolean.parseBoolean(f[13].trim()));
    }

    public static String categoryId(String category, String storeId) {
        return "cat-" + category.toLowerCase(Locale.ROOT) + "-" + storeId;
    }
}
