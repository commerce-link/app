package pl.commercelink.marketplace;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CsvMarketplaceExportRunReaderTest {

    private static final String KEY =
            "marketplace-export-runs/store-1/allegro/catalog-1/2026-08-13_01-31-05.csv";

    private final CsvMarketplaceExportRunReader reader = new CsvMarketplaceExportRunReader();

    @Test
    void handlesOnlyCsvKeys() {
        // when / then
        assertThat(reader.handlesFileFormatOf(KEY)).isTrue();
        assertThat(reader.handlesFileFormatOf(KEY.replace(".csv", ".json"))).isFalse();
    }

    @Test
    void parsesFourColumnRows() {
        // given
        byte[] fileContent = csv("pim-1;1999;7;2");

        // when
        MarketplaceExportRunDocument document = reader.parse(KEY, fileContent);

        // then
        assertThat(document.offers()).hasSize(1);
        MarketplaceOfferSnapshot snapshot = document.offers().get(0);
        assertThat(snapshot.pimId()).isEqualTo("pim-1");
        assertThat(snapshot.price()).isEqualTo(1999L);
        assertThat(snapshot.quantity()).isEqualTo(7L);
        assertThat(snapshot.removalAttempts()).isEqualTo(2);
        assertThat(snapshot.pendingRemoval()).isFalse();
        assertThat(snapshot.quantityZeroedReason()).isNull();
    }

    @Test
    void parsesLegacyThreeColumnRowsWithoutRemovalAttempts() {
        // given
        byte[] fileContent = csv("pim-1;1999;7");

        // when
        MarketplaceExportRunDocument document = reader.parse(KEY, fileContent);

        // then
        assertThat(document.offers().get(0).removalAttempts()).isEqualTo(0);
    }

    @Test
    void throwsOnUnsupportedColumnCount() {
        // when / then
        assertThatThrownBy(() -> reader.parse(KEY, csv("pim-1;1999")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> reader.parse(KEY, csv("pim-1;1999;7;2;extra")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void buildsDocumentWithRunIdFromTheKeyAndSuccessfulStatus() {
        // when
        MarketplaceExportRunDocument document = reader.parse(KEY, csv("pim-1;1999;7;0"));

        // then
        assertThat(document.runId()).isEqualTo("2026-08-13_01-31-05");
        assertThat(document.wasSuccessful()).isTrue();
        assertThat(document.excluded()).isEmpty();
    }

    private byte[] csv(String row) {
        return ("pimId;price;qty;removalAttempts\n" + row + "\n").getBytes(StandardCharsets.UTF_8);
    }
}
