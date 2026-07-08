package pl.commercelink.localdev;

import org.junit.jupiter.api.Test;
import pl.commercelink.pricelist.AvailabilityAndPrice;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogSeedConsistencyTest {


    private static final Set<String> ACME_MAPPED_CATEGORIES = Set.of(
            "CPU", "GPU", "Memory", "Motherboard", "PSU", "Storage", "Cooler", "Case", "Fan",
            "Displays", "Keyboards", "Mice", "MousePads", "Headphones", "Microphones", "Webcams", "Speakers", "Laptops");

    private final List<CatalogSeedRow> rows = CatalogSeed.load();

    @Test
    void loadReturnsExactlyOneRowPerSourceLine() throws Exception {
        // given
        long sourceDataLines;
        try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(
                CatalogSeed.class.getResourceAsStream(CatalogSeed.RESOURCE), java.nio.charset.StandardCharsets.UTF_8))) {
            sourceDataLines = reader.lines().skip(1).filter(line -> !line.isBlank()).count();
        }

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
    void everyProductResolvesToAFeedRowWithMatchingEanAndMfn() {
        // given
        Map<String, FeedRow> feedByMfn = allFeedRows().stream()
                .collect(Collectors.toMap(FeedRow::mfn, Function.identity(), (a, b) -> a));

        // when / then
        for (CatalogSeedRow row : rows) {
            FeedRow feedRow = feedByMfn.get(row.mfn());
            assertThat(feedRow).as("feed row for MFN %s (%s)", row.mfn(), row.pimId()).isNotNull();
            assertThat(feedRow.mfn()).isNotBlank();
            assertThat(feedRow.ean()).isNotBlank();
            assertThat(feedRow.ean()).as("feed/product EAN agreement for %s", row.pimId()).isEqualTo(row.ean());
        }
    }

    @Test
    void everyCatalogProductHasAPricelistRowAndFeedOnlyRowsStayOut() {
        // given
        Map<String, AvailabilityAndPrice> pricelistByMfn = CatalogSeed.pricelist(rows).stream()
                .collect(Collectors.toMap(AvailabilityAndPrice::getManufacturerCode, Function.identity(), (a, b) -> a));

        // when / then
        for (CatalogSeedRow row : rows) {
            AvailabilityAndPrice priced = pricelistByMfn.get(row.mfn());
            if (row.inCatalog()) {
                assertThat(priced).as("pricelist row for %s", row.pimId()).isNotNull();
                assertThat(priced.getEan()).isEqualTo(row.ean());
            } else {
                assertThat(priced).as("feed-only row %s must stay out of the pricelist", row.pimId()).isNull();
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
        // given
        List<String> acmeMfns = CatalogSeed.acmeFeed(rows).stream().map(FeedRow::mfn).toList();

        // when / then
        assertThat(acmeMfns).contains("MFN-CLEAR-01", "MFN-TWIN-01");
    }

    @Test
    void pricelistColumnOrderMatchesTheReaderContract() {
        // given
        String[] expected = {"PimId", "EAN", "Mfn", "Brand", "Label", "Name", "Category", "Price", "Qty",
                "Estimated Delivery Days", "Lowest 30 Days Price"};

        // when / then
        assertThat(AvailabilityAndPrice.HEADERS).containsExactly(expected);
    }

    @Test
    void acmeFeedNetPriceIsBelowPricelistGrossPrice() {
        // given
        Map<String, CatalogSeedRow> rowByMfn = rows.stream()
                .collect(Collectors.toMap(CatalogSeedRow::mfn, Function.identity()));

        // when / then
        for (FeedRow feedRow : allFeedRows()) {
            double net = Double.parseDouble(feedRow.price());
            int gross = rowByMfn.get(feedRow.mfn()).priceGross();
            assertThat(net).as("net < gross for %s", feedRow.mfn()).isLessThan(gross);
        }
    }

    private List<FeedRow> allFeedRows() {
        List<FeedRow> feed = new java.util.ArrayList<>(CatalogSeed.acmeFeed(rows));
        feed.addAll(CatalogSeed.acmeBFeed(rows));
        return feed;
    }

    private static void assertNoDuplicates(List<CatalogSeedRow> rows, Function<CatalogSeedRow, String> key, String field) {
        Map<String, Long> counts = rows.stream().collect(Collectors.groupingBy(key, Collectors.counting()));
        List<String> duplicates = counts.entrySet().stream().filter(e -> e.getValue() > 1).map(Map.Entry::getKey).toList();
        assertThat(duplicates).as("duplicate %s values", field).isEmpty();
    }
}
