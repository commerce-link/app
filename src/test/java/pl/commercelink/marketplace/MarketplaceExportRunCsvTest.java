package pl.commercelink.marketplace;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MarketplaceExportRunCsvTest {

    @Test
    void writesAndReadsBackEveryRow() {
        // given
        List<MarketplaceOfferSnapshot> rows = List.of(
                MarketplaceOfferSnapshot.published("pim-A", 1999L, 7L, "EAN-A", "MFN-A"),
                MarketplaceOfferSnapshot.removalPending("pim-B", 999L, 2, "EAN-B", null),
                MarketplaceOfferSnapshot.published("pim-D", 300L, 2L, "EAN-D", "MFN-D").withWaitingRuns(2),
                MarketplaceOfferSnapshot.published("pim-C", 500L, 1L).rejected("VALIDATION", "price too low"),
                MarketplaceOfferSnapshot.exportAborted("java.lang.IllegalStateException: marketplace unavailable"));

        // when
        List<MarketplaceOfferSnapshot> readBack = MarketplaceExportRunCsv.parse(MarketplaceExportRunCsv.toBytes(rows));

        // then
        assertThat(readBack).isEqualTo(rows.stream()
                .map(row -> new MarketplaceOfferSnapshot(
                        row.pimId(), row.price(), row.quantity(), row.removalAttempts(),
                        row.outcome(),
                        row.reasonCode() == null ? "" : row.reasonCode(),
                        row.message() == null ? "" : row.message(),
                        row.ean(), row.manufacturerCode(), row.waitingRuns()))
                .toList());
    }

    @Test
    void writesTheTenColumnHeader() {
        // when
        String csv = new String(MarketplaceExportRunCsv.toBytes(
                List.of(MarketplaceOfferSnapshot.published("pim-A", 1999L, 7L))), StandardCharsets.UTF_8);

        // then
        assertThat(csv.lines().findFirst()).hasValue(
                "\"pimId\";\"price\";\"quantity\";\"removalAttempts\";\"outcome\";\"reasonCode\";\"message\";\"ean\";\"manufacturerCode\";\"waitingRuns\"");
    }

    @Test
    void readsLegacyThreeColumnRowsWithoutRemovalAttempts() {
        // given
        byte[] content = csv("pim-1;1999;7");

        // when
        List<MarketplaceOfferSnapshot> rows = MarketplaceExportRunCsv.parse(content);

        // then
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).pimId()).isEqualTo("pim-1");
        assertThat(rows.get(0).price()).isEqualTo(1999L);
        assertThat(rows.get(0).quantity()).isEqualTo(7L);
        assertThat(rows.get(0).removalAttempts()).isZero();
        assertThat(rows.get(0).outcome()).isEmpty();
    }

    @Test
    void readsLegacyFourColumnRows() {
        // given
        byte[] content = csv("pim-1;1999;7;2");

        // when
        List<MarketplaceOfferSnapshot> rows = MarketplaceExportRunCsv.parse(content);

        // then
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).removalAttempts()).isEqualTo(2);
        assertThat(rows.get(0).outcome()).isEmpty();
        assertThat(rows.get(0).reasonCode()).isEmpty();
        assertThat(rows.get(0).message()).isEmpty();
    }

    @Test
    void skipsRowsWithTooFewColumnsInsteadOfFailingTheWholeFile() {
        // given
        byte[] content = csv("pim-1;1999\npim-2;1999;7;0;PUBLISHED;;");

        // when
        List<MarketplaceOfferSnapshot> rows = MarketplaceExportRunCsv.parse(content);

        // then
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).pimId()).isEqualTo("pim-2");
    }

    @Test
    void runIdDropsDirectoriesExtensionAndTheFailedSuffix() {
        // when / then
        assertThat(MarketplaceExportRunCsv.runIdFrom(
                "store-1/marketplace-exports/allegro/catalog-1/2026-08-13_01-31-05.csv"))
                .isEqualTo("2026-08-13_01-31-05");
        assertThat(MarketplaceExportRunCsv.runIdFrom(
                "store-1/marketplace-exports/allegro/catalog-1/2026-08-13_01-31-05-failed.csv"))
                .isEqualTo("2026-08-13_01-31-05");
    }

    @Test
    void normalizesMessageByCollapsingWhitespaceWhileKeepingQuotesAndSemicolons() {
        // given
        String message = "  {\n  \"errors\": [ {\"code\": \"INVALID\"; \"message\": \"too low\"} ]\n}  ";

        // when
        String normalized = MarketplaceExportRunCsv.normalizeMessage(message);

        // then
        assertThat(normalized).isEqualTo("{ \"errors\": [ {\"code\": \"INVALID\"; \"message\": \"too low\"} ] }");
        assertThat(normalized).doesNotContain("\n");
    }

    @Test
    void normalizesNullMessageToNull() {
        // when / then
        assertThat(MarketplaceExportRunCsv.normalizeMessage(null)).isNull();
    }

    @Test
    void truncatesTheNormalizedMessageToOneThousandCharactersAndMarksItWithAnEllipsis() {
        // when
        String normalized = MarketplaceExportRunCsv.normalizeMessage("x".repeat(1200));

        // then
        assertThat(normalized).hasSize(1001);
        assertThat(normalized).isEqualTo("x".repeat(1000) + "\u2026");
    }

    @Test
    void readsSevenColumnRowsWrittenBeforeTheIdentifiersWereRecordedWithoutEan() {
        // given
        byte[] content = ("pimId;price;quantity;removalAttempts;outcome;reasonCode;message\n"
                + "pim-1;1999;0;2;REMOVAL_PENDING;;\n").getBytes(StandardCharsets.UTF_8);

        // when
        List<MarketplaceOfferSnapshot> rows = MarketplaceExportRunCsv.parse(content);

        // then
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).removalAttempts()).isEqualTo(2);
        assertThat(rows.get(0).ean()).isNull();
        assertThat(rows.get(0).manufacturerCode()).isNull();
    }

    @Test
    void readsNineColumnRowsWrittenBeforeWaitingRunsWereRecordedWithZeroWaitingRuns() {
        // given
        byte[] content = ("pimId;price;quantity;removalAttempts;outcome;reasonCode;message;ean;manufacturerCode\n"
                + "pim-1;1999;7;0;PUBLISHED;;;5900000000011;M1\n").getBytes(StandardCharsets.UTF_8);

        // when
        List<MarketplaceOfferSnapshot> rows = MarketplaceExportRunCsv.parse(content);

        // then
        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.ean()).isEqualTo("5900000000011");
            assertThat(row.waitingRuns()).isZero();
        });
    }

    @Test
    void leavesAMessageShorterThanTheLimitWithoutAnEllipsis() {
        // when
        String normalized = MarketplaceExportRunCsv.normalizeMessage("x".repeat(1000));

        // then
        assertThat(normalized).hasSize(1000);
        assertThat(normalized).doesNotContain("\u2026");
    }

    @Test
    void appliesMessageNormalizationWhenWritingNotWhenReading() {
        // given
        MarketplaceOfferSnapshot row = MarketplaceOfferSnapshot.published("pim-A", 1999L, 7L)
                .rejected("VALIDATION", "line one\nline two");

        // when
        List<MarketplaceOfferSnapshot> readBack = MarketplaceExportRunCsv.parse(
                MarketplaceExportRunCsv.toBytes(List.of(row)));

        // then
        assertThat(readBack.get(0).message()).isEqualTo("line one line two");
    }

    private byte[] csv(String rows) {
        return ("pimId;price;quantity;removalAttempts\n" + rows + "\n").getBytes(StandardCharsets.UTF_8);
    }
}
