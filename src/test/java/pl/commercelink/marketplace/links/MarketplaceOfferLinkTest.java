package pl.commercelink.marketplace.links;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MarketplaceOfferLinkTest {

    @Test
    void linkKeyJoinsMarketplaceAndOfferKey() {
        // when
        String key = MarketplaceOfferLink.linkKey("Empik", "PS-8001");

        // then
        assertThat(key).isEqualTo("Empik#PS-8001");
    }

    @Test
    void ofFillsAllFieldsAndDerivesTheLinkKey() {
        // given
        Instant now = Instant.parse("2026-11-02T10:15:30Z");

        // when
        MarketplaceOfferLink link = MarketplaceOfferLink.of("store-1", "Empik", "PS-8001",
                List.of("5901234123457"), MarketplaceOfferLinkSource.MANUAL, "user@x.pl", now);

        // then
        assertThat(link.getStoreId()).isEqualTo("store-1");
        assertThat(link.getLinkKey()).isEqualTo("Empik#PS-8001");
        assertThat(link.getMarketplace()).isEqualTo("Empik");
        assertThat(link.getSellerOfferKey()).isEqualTo("PS-8001");
        assertThat(link.getEans()).containsExactly("5901234123457");
        assertThat(link.getSource()).isEqualTo(MarketplaceOfferLinkSource.MANUAL);
        assertThat(link.getCreatedBy()).isEqualTo("user@x.pl");
        assertThat(link.getCreatedAt()).isEqualTo(now);
    }

    @Test
    void ofTrimsPaddedEansBeforeUnifyingThem() {
        // when
        MarketplaceOfferLink link = MarketplaceOfferLink.of("store-1", "Empik", "K-1",
                List.of(" 05901234123457 ", "5901234123457"), MarketplaceOfferLinkSource.MANUAL, null, Instant.EPOCH);

        // then
        assertThat(link.getEans()).containsExactly("5901234123457");
    }

    @Test
    void ofUnifiesEansDropsBlanksAndDuplicatesKeepingTheFirstAppearanceOrder() {
        // when
        MarketplaceOfferLink link = MarketplaceOfferLink.of("store-1", "Empik", "K-1",
                Arrays.asList("05901234123457", " ", null, "5906789000011", "5901234123457", ""),
                MarketplaceOfferLinkSource.EAN, null, Instant.EPOCH);

        // then
        assertThat(link.getEans()).containsExactly("5901234123457", "5906789000011");
    }

    @Test
    void instantConverterRoundTripsIsoText() {
        // given
        MarketplaceOfferLinkInstantConverter converter = new MarketplaceOfferLinkInstantConverter();
        Instant at = Instant.parse("2026-11-02T10:15:30.123Z");

        // when
        String text = converter.convert(at);

        // then
        assertThat(text).isEqualTo("2026-11-02T10:15:30.123Z");
        assertThat(converter.unconvert(text)).isEqualTo(at);
        assertThat(converter.convert(null)).isNull();
        assertThat(converter.unconvert(null)).isNull();
    }
}
