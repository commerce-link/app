package pl.commercelink.marketplace.links;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class MarketplaceOfferLinkTest {

    @Test
    void linkKeyJoinsMarketplaceAndOfferKey() {
        // when
        String key = MarketplaceOfferLink.linkKey("Empik", "SILLO-84980");

        // then
        assertThat(key).isEqualTo("Empik#SILLO-84980");
    }

    @Test
    void ofFillsAllFieldsAndDerivesTheLinkKey() {
        // given
        Instant now = Instant.parse("2026-11-02T10:15:30Z");

        // when
        MarketplaceOfferLink link = MarketplaceOfferLink.of("store-1", "Empik", "SILLO-84980", "pim-1",
                MarketplaceOfferLinkSource.MANUAL, "user@x.pl", now);

        // then
        assertThat(link.getStoreId()).isEqualTo("store-1");
        assertThat(link.getLinkKey()).isEqualTo("Empik#SILLO-84980");
        assertThat(link.getMarketplace()).isEqualTo("Empik");
        assertThat(link.getSellerOfferKey()).isEqualTo("SILLO-84980");
        assertThat(link.getPimId()).isEqualTo("pim-1");
        assertThat(link.getSource()).isEqualTo(MarketplaceOfferLinkSource.MANUAL);
        assertThat(link.getCreatedBy()).isEqualTo("user@x.pl");
        assertThat(link.getCreatedAt()).isEqualTo(now);
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
