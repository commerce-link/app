package pl.commercelink.localdev;

import pl.commercelink.invoicing.api.Price;
import pl.commercelink.pricelist.AvailabilityAndPrice;
import pl.commercelink.starter.csv.CSVLoader;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class CatalogSeed {

    public static final String RESOURCE = "/local-init/seed/catalog.csv";
    public static final String ACME = "Acme";
    public static final String ACME_B = "AcmeB";

    private static final String CURRENCY = "PLN";
    private static final BigDecimal VAT_RATE = BigDecimal.valueOf(Price.DEFAULT_VAT_RATE);
    private static final BigDecimal ACME_B_PRICE_FACTOR = new BigDecimal("0.97");
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

    public static List<FeedRow> acmeFeed(List<CatalogSeedRow> rows) {
        return feed(rows, ACME, BigDecimal.ONE);
    }

    public static List<FeedRow> acmeBFeed(List<CatalogSeedRow> rows) {
        return feed(rows, ACME_B, ACME_B_PRICE_FACTOR);
    }

    private static List<FeedRow> feed(List<CatalogSeedRow> rows, String supplier, BigDecimal priceFactor) {
        List<FeedRow> feed = new ArrayList<>();
        for (CatalogSeedRow row : rows) {
            if (row.soldBy(supplier)) {
                feed.add(new FeedRow(row.ean(), row.mfn(), row.brand(), row.name(), row.category(),
                        netPrice(row.priceGross(), priceFactor), CURRENCY, String.valueOf(row.qty())));
            }
        }
        return feed;
    }

    public static List<AvailabilityAndPrice> pricelist(List<CatalogSeedRow> rows) {
        List<AvailabilityAndPrice> pricelist = new ArrayList<>();
        for (CatalogSeedRow row : rows) {
            if (!row.inCatalog()) {
                continue;
            }
            pricelist.add(new AvailabilityAndPrice(
                    row.pimId(), row.ean(), row.mfn(), row.brand(), row.label(), row.name(), row.category(),
                    row.priceGross(), row.qty(), row.estimatedDeliveryDays(), row.lowest30DaysPrice()));
        }
        return pricelist;
    }

    static String netPrice(int gross, BigDecimal priceFactor) {
        return BigDecimal.valueOf(gross)
                .divide(VAT_RATE, 4, RoundingMode.HALF_UP)
                .multiply(priceFactor)
                .setScale(2, RoundingMode.HALF_UP)
                .toPlainString();
    }
}
