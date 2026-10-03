package pl.commercelink.baskets;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class OfferValidityTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 3, 12, 0);

    @Test
    void noDateMeansNoExpiry() {
        // when / then
        assertThat(OfferValidity.of(null, NOW)).isEqualTo(OfferValidity.NO_EXPIRY);
    }

    @Test
    void pastDateIsExpiredLikeBasketIsExpired() {
        // given
        Basket basket = new Basket();
        basket.setType(BasketType.Offer);
        basket.setExpiresAt(NOW.minusMinutes(1));

        // when / then
        assertThat(OfferValidity.of(NOW.minusMinutes(1), NOW)).isEqualTo(OfferValidity.EXPIRED);
        assertThat(basket.isExpired()).isTrue();
    }

    @Test
    void theExactMomentIsStillValid() {
        // when / then — Basket.isExpired uses now.isAfter(expiresAt), so equality is not expired
        assertThat(OfferValidity.of(NOW, NOW)).isEqualTo(OfferValidity.EXPIRING);
    }

    @Test
    void withinSevenDaysIsExpiring() {
        // when / then
        assertThat(OfferValidity.of(NOW.plusHours(2), NOW)).isEqualTo(OfferValidity.EXPIRING);
        assertThat(OfferValidity.of(NOW.plusDays(7), NOW)).isEqualTo(OfferValidity.EXPIRING);
    }

    @Test
    void laterThanSevenDaysIsActive() {
        // when / then
        assertThat(OfferValidity.of(NOW.plusDays(7).plusMinutes(1), NOW)).isEqualTo(OfferValidity.ACTIVE);
    }

    @Test
    void parsesItsParamsAndIgnoresUnknownValues() {
        // when / then
        assertThat(OfferValidity.parse("noExpiry")).contains(OfferValidity.NO_EXPIRY);
        assertThat(OfferValidity.parse(" EXPIRING ")).contains(OfferValidity.EXPIRING);
        assertThat(OfferValidity.parse("soon")).isEmpty();
        assertThat(OfferValidity.parse(null)).isEmpty();
    }
}
