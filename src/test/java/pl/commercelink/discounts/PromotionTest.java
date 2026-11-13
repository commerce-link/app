package pl.commercelink.discounts;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class PromotionTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 12, 12, 0);

    @Test
    void statusFollowsPrecedence() {
        // given
        Promotion disabledAndExpired = promotion(false, null, NOW.minusDays(1));
        Promotion expiredAndScheduled = promotion(true, NOW.plusDays(1), NOW.minusDays(1));
        Promotion scheduled = promotion(true, NOW.plusDays(1), null);
        Promotion active = promotion(true, NOW.minusDays(1), NOW.plusDays(1));
        Promotion withoutDates = promotion(true, null, null);

        // when / then
        assertThat(disabledAndExpired.statusAt(NOW)).isEqualTo(DiscountCodeStatus.Disabled);
        assertThat(expiredAndScheduled.statusAt(NOW)).isEqualTo(DiscountCodeStatus.Expired);
        assertThat(scheduled.statusAt(NOW)).isEqualTo(DiscountCodeStatus.Scheduled);
        assertThat(active.statusAt(NOW)).isEqualTo(DiscountCodeStatus.Active);
        assertThat(withoutDates.statusAt(NOW)).isEqualTo(DiscountCodeStatus.Active);
    }

    @Test
    void bothEndsOfTheValidityCount() {
        // given
        Promotion promotion = promotion(true, NOW, NOW);

        // when / then
        assertThat(promotion.statusAt(NOW)).isEqualTo(DiscountCodeStatus.Active);
        assertThat(promotion.statusAt(NOW.plusSeconds(1))).isEqualTo(DiscountCodeStatus.Expired);
        assertThat(promotion.statusAt(NOW.minusSeconds(1))).isEqualTo(DiscountCodeStatus.Scheduled);
    }

    private static Promotion promotion(boolean active, LocalDateTime validFrom, LocalDateTime validTo) {
        Promotion promotion = new Promotion();
        promotion.setActive(active);
        promotion.setValidFrom(validFrom);
        promotion.setValidTo(validTo);
        return promotion;
    }
}
