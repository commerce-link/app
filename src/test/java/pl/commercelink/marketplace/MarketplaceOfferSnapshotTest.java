package pl.commercelink.marketplace;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MarketplaceOfferSnapshotTest {

    @Test
    void csvHeadersIncludePimIdPriceQtyAndRemovalAttempts() {
        assertThat(MarketplaceOfferSnapshot.csvHeaders())
                .containsExactly("pimId", "price", "qty", "removalAttempts");
    }

    @Test
    void asStringArraySerializesAllFourFieldsInOrder() {
        MarketplaceOfferSnapshot snapshot = new MarketplaceOfferSnapshot("pim-1", 12345L, 7L, 2);

        assertThat(snapshot.asStringArray())
                .containsExactly("pim-1", "12345", "7", "2");
    }

    @Test
    void fromStringArrayParsesFourColumnCurrentFormat() {
        MarketplaceOfferSnapshot snapshot = MarketplaceOfferSnapshot.fromStringArray(
                new String[]{"pim-1", "12345", "7", "2"}
        );

        assertThat(snapshot.getPimId()).isEqualTo("pim-1");
        assertThat(snapshot.getPrice()).isEqualTo(12345L);
        assertThat(snapshot.getQty()).isEqualTo(7L);
        assertThat(snapshot.getRemovalAttempts()).isEqualTo(2);
    }

    @Test
    void fromStringArrayParsesLegacyThreeColumnFormatWithoutRemovalAttempts() {
        MarketplaceOfferSnapshot snapshot = MarketplaceOfferSnapshot.fromStringArray(
                new String[]{"pim-1", "12345", "7"}
        );

        assertThat(snapshot.getPimId()).isEqualTo("pim-1");
        assertThat(snapshot.getPrice()).isEqualTo(12345L);
        assertThat(snapshot.getQty()).isEqualTo(7L);
        assertThat(snapshot.getRemovalAttempts()).isEqualTo(0);
    }

    @Test
    void fromStringArrayThrowsOnTwoColumnInput() {
        assertThatThrownBy(() -> MarketplaceOfferSnapshot.fromStringArray(new String[]{"pim-1", "12345"}))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fromStringArrayThrowsOnFiveColumnInput() {
        assertThatThrownBy(() -> MarketplaceOfferSnapshot.fromStringArray(new String[]{"a", "b", "c", "d", "e"}))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
