package pl.commercelink.localdev;

import org.junit.jupiter.api.Test;
import pl.commercelink.pricelist.AvailabilityAndPrice;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogSeedConsistencyTest {

    private static final String ACME_FEED = "/local-init/s3/feeds/acme-feed.csv";
    private static final String ACME_B_FEED = "/local-init/s3/feeds/acmeb-feed.csv";
    private static final String PRICELIST = "/local-init/s3/stores/uma2dqukxr/pricelists/cat-local-01/seed.csv";
    private static final String FEED_HEADER = "ean;mfn;brand;name;category;price;currency;qty";

    private static final Set<String> ACME_MAPPED_CATEGORIES = Set.of(
            "CPU", "GPU", "Memory", "Motherboard", "PSU", "Storage", "Cooler", "Case", "Fan",
            "Displays", "Keyboards", "Mice", "MousePads", "Headphones", "Microphones", "Webcams", "Speakers", "Laptops");

    private final List<CatalogSeedRow> rows = CatalogSeed.load();

    @Test
    void loadReturnsExactlyOneRowPerSourceLine() {
        // given
        long sourceDataLines = readLines(CatalogSeed.RESOURCE).size() - 1;

        // when / then
        assertThat(rows).hasSize((int) sourceDataLines);
    }

    @Test
    void everyRowHasNonBlankEanMfnCategoryAndSupplier() {
        // when / then
        assertThat(rows).isNotEmpty();
        for (CatalogSeedRow row : rows) {
            assertThat(row.ean()).as("ean of %s", row.pimId()).isNotBlank();
            assertThat(row.mfn()).as("mfn of %s", row.pimId()).isNotBlank();
            assertThat(row.category()).as("category of %s", row.pimId()).isNotBlank();
            assertThat(row.suppliers()).as("suppliers of %s", row.pimId()).isNotEmpty();
        }
    }

    @Test
    void everyCategoryIsSupplierMappedSoProductsEnterInventory() {
        // when / then
        for (CatalogSeedRow row : rows) {
            assertThat(ACME_MAPPED_CATEGORIES)
                    .as("category '%s' of %s is not Acme-mapped -> would drop out of inventory (Realizacja)", row.category(), row.pimId())
                    .contains(row.category());
        }
    }

    @Test
    void identifiersAreUnique() {
        // when / then
        assertNoDuplicates(rows, CatalogSeedRow::pimId, "pimId");
        assertNoDuplicates(rows, CatalogSeedRow::ean, "ean");
        assertNoDuplicates(rows, CatalogSeedRow::mfn, "mfn");
    }

    @Test
    void committedFeedsStartWithTheParserHeader() {
        // given - CSVLoader skips line 0, so a headerless feed silently swallows its first product
        // when / then
        assertThat(readLines(ACME_FEED).get(0)).isEqualTo(FEED_HEADER);
        assertThat(readLines(ACME_B_FEED).get(0)).isEqualTo(FEED_HEADER);
    }

    @Test
    void everyProductResolvesToACommittedFeedRowWithMatchingEanAndMfn() {
        // given
        Map<String, Map<String, String[]>> feedsBySupplier = Map.of(
                "Acme", feedByMfn(ACME_FEED),
                "AcmeB", feedByMfn(ACME_B_FEED));

        // when / then
        for (CatalogSeedRow row : rows) {
            for (String supplier : row.suppliers()) {
                String[] feedRow = feedsBySupplier.get(supplier).get(row.mfn());
                assertThat(feedRow).as("%s feed row for MFN %s (%s)", supplier, row.mfn(), row.pimId()).isNotNull();
                assertThat(feedRow[0]).as("feed/catalog EAN agreement for %s", row.pimId()).isEqualTo(row.ean());
                assertThat(feedRow[4]).as("feed/catalog category agreement for %s", row.pimId()).isEqualTo(row.category());
                assertThat(feedRow[7]).as("feed/catalog qty agreement for %s", row.pimId()).isEqualTo(String.valueOf(row.qty()));
            }
        }
    }

    @Test
    void committedFeedsContainOnlyAndAllCatalogRows() {
        // given
        Map<String, CatalogSeedRow> rowByMfn = rows.stream()
                .collect(Collectors.toMap(CatalogSeedRow::mfn, Function.identity()));

        // when / then
        for (Map.Entry<String, String> feed : Map.of("Acme", ACME_FEED, "AcmeB", ACME_B_FEED).entrySet()) {
            Map<String, String[]> feedRows = feedByMfn(feed.getValue());
            long expected = rows.stream().filter(row -> row.soldBy(feed.getKey())).count();
            assertThat(feedRows).as("%s feed row count", feed.getKey()).hasSize((int) expected);
            for (String mfn : feedRows.keySet()) {
                CatalogSeedRow row = rowByMfn.get(mfn);
                assertThat(row).as("stale %s feed row with MFN %s missing from catalog.csv", feed.getKey(), mfn).isNotNull();
                assertThat(row.soldBy(feed.getKey())).as("MFN %s is not sold by %s in catalog.csv", mfn, feed.getKey()).isTrue();
            }
        }
    }

    @Test
    void committedPricelistMatchesCatalogProductsExactly() {
        // given
        List<String> lines = readLines(PRICELIST);
        Map<String, String[]> pricelistByMfn = lines.stream().skip(1)
                .map(line -> line.split(";", -1))
                .collect(Collectors.toMap(f -> f[2], Function.identity()));
        Map<String, CatalogSeedRow> catalogRowsByMfn = rows.stream()
                .filter(CatalogSeedRow::inCatalog)
                .collect(Collectors.toMap(CatalogSeedRow::mfn, Function.identity()));

        // when / then
        assertThat(lines.get(0)).isEqualTo(String.join(";", AvailabilityAndPrice.HEADERS));
        assertThat(pricelistByMfn.keySet()).isEqualTo(catalogRowsByMfn.keySet());
        for (CatalogSeedRow row : catalogRowsByMfn.values()) {
            String[] priced = pricelistByMfn.get(row.mfn());
            assertThat(priced[1]).as("pricelist EAN of %s", row.pimId()).isEqualTo(row.ean());
            assertThat(priced[7]).as("pricelist gross price of %s", row.pimId()).isEqualTo(String.valueOf(row.priceGross()));
        }
    }

    @Test
    void committedFeedNetPricesAreBelowCatalogGrossPrices() {
        // given
        Map<String, CatalogSeedRow> rowByMfn = rows.stream()
                .collect(Collectors.toMap(CatalogSeedRow::mfn, Function.identity()));

        // when / then
        for (String feed : List.of(ACME_FEED, ACME_B_FEED)) {
            for (String[] feedRow : feedByMfn(feed).values()) {
                double net = Double.parseDouble(feedRow[5]);
                int gross = rowByMfn.get(feedRow[1]).priceGross();
                assertThat(net).as("net < gross for %s in %s", feedRow[1], feed).isLessThan(gross);
            }
        }
    }

    @Test
    void acmeBIsAlwaysCheaperThanAcmeForSharedProducts() {
        // given
        Map<String, String[]> acme = feedByMfn(ACME_FEED);
        Map<String, String[]> acmeB = feedByMfn(ACME_B_FEED);

        // when / then
        for (String mfn : acme.keySet()) {
            if (acmeB.containsKey(mfn)) {
                assertThat(Double.parseDouble(acmeB.get(mfn)[5]))
                        .as("AcmeB price for %s", mfn)
                        .isLessThan(Double.parseDouble(acme.get(mfn)[5]));
            }
        }
    }

    @Test
    void everyCategoryHasFeedOnlyRowsSoRecommendationsAreTestable() {
        // given
        Map<String, List<CatalogSeedRow>> feedOnlyByCategory = rows.stream()
                .filter(row -> !row.inCatalog())
                .collect(Collectors.groupingBy(CatalogSeedRow::category));
        List<String> categories = rows.stream().map(CatalogSeedRow::category).distinct().toList();

        // when / then
        for (String category : categories) {
            assertThat(feedOnlyByCategory.getOrDefault(category, List.of()))
                    .as("feed-only rows of category %s feed the recommendations screen", category)
                    .hasSizeGreaterThanOrEqualTo(3);
        }
    }

    @Test
    void feedOnlyRowsAreNeverWarehouseItems() {
        // when / then
        for (CatalogSeedRow row : rows) {
            if (!row.inCatalog()) {
                assertThat(row.inWarehouse()).as("feed-only row %s cannot sit in the warehouse", row.pimId()).isFalse();
            }
        }
    }

    @Test
    void seededDemoOrderItemsAreFulfillableFromTheAcmeFeed() {
        // when / then
        assertThat(feedByMfn(ACME_FEED).keySet()).contains("MFN-CLEAR-01", "MFN-TWIN-01");
    }

    private static Map<String, String[]> feedByMfn(String resource) {
        return readLines(resource).stream().skip(1)
                .map(line -> line.split(";", -1))
                .collect(Collectors.toMap(f -> f[1], Function.identity()));
    }

    private static List<String> readLines(String resource) {
        InputStream stream = CatalogSeed.class.getResourceAsStream(resource);
        assertThat(stream).as("resource %s present", resource).isNotNull();
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank()) {
                    lines.add(line);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read " + resource, e);
        }
        return lines;
    }

    private static void assertNoDuplicates(List<CatalogSeedRow> rows, Function<CatalogSeedRow, String> key, String field) {
        Map<String, Long> counts = rows.stream().collect(Collectors.groupingBy(key, Collectors.counting()));
        List<String> duplicates = counts.entrySet().stream().filter(e -> e.getValue() > 1).map(Map.Entry::getKey).toList();
        assertThat(duplicates).as("duplicate %s values", field).isEmpty();
    }
}
