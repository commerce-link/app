package pl.commercelink.discounts;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class DiscountCodeTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 24, 12, 0);

    @Test
    void normalizeTrimsAndUppercases() {
        // when / then
        assertThat(DiscountCode.normalize("  lato-10 ")).isEqualTo("LATO-10");
        assertThat(DiscountCode.normalize(null)).isNull();
    }

    @Test
    void isWellFormedAcceptsOnlyLettersDigitsAndHyphen() {
        // when / then
        assertThat(DiscountCode.isWellFormed("LATO-10")).isTrue();
        assertThat(DiscountCode.isWellFormed("LATO_10")).isFalse();
        assertThat(DiscountCode.isWellFormed("ZNIŻKA")).isFalse();
        assertThat(DiscountCode.isWellFormed("LATO 10")).isFalse();
        assertThat(DiscountCode.isWellFormed("lato-10")).isFalse();
        assertThat(DiscountCode.isWellFormed(null)).isFalse();
    }

    @Test
    void isWellFormedAcceptsThreeToThirtyTwoCharacters() {
        // when / then
        assertThat(DiscountCode.isWellFormed("ABC")).isTrue();
        assertThat(DiscountCode.isWellFormed("A".repeat(32))).isTrue();
        assertThat(DiscountCode.isWellFormed("AB")).isFalse();
        assertThat(DiscountCode.isWellFormed("A".repeat(33))).isFalse();
    }

    @Test
    void statusFollowsPrecedence() {
        // given
        DiscountCode disabledExhaustedExpired = code(false, 1, 1, null, NOW.minusDays(1));
        DiscountCode exhaustedAndExpired = code(true, 1, 1, null, NOW.minusDays(1));
        DiscountCode expiredAndScheduled = code(true, 0, 0, NOW.plusDays(1), NOW.minusDays(1));
        DiscountCode scheduled = code(true, 5, 1, NOW.plusDays(1), null);
        DiscountCode active = code(true, 5, 1, NOW.minusDays(1), NOW.plusDays(1));

        // when / then
        assertThat(disabledExhaustedExpired.statusAt(NOW)).isEqualTo(DiscountCodeStatus.Disabled);
        assertThat(exhaustedAndExpired.statusAt(NOW)).isEqualTo(DiscountCodeStatus.Exhausted);
        assertThat(expiredAndScheduled.statusAt(NOW)).isEqualTo(DiscountCodeStatus.Expired);
        assertThat(scheduled.statusAt(NOW)).isEqualTo(DiscountCodeStatus.Scheduled);
        assertThat(active.statusAt(NOW)).isEqualTo(DiscountCodeStatus.Active);
    }

    @Test
    void statusBoundariesAreInclusive() {
        // given
        DiscountCode code = code(true, 0, 0, NOW, NOW.plusHours(1));

        // when / then
        assertThat(code.statusAt(NOW)).isEqualTo(DiscountCodeStatus.Active);
        assertThat(code.statusAt(NOW.plusHours(1))).isEqualTo(DiscountCodeStatus.Active);
        assertThat(code.statusAt(NOW.minusSeconds(1))).isEqualTo(DiscountCodeStatus.Scheduled);
        assertThat(code.statusAt(NOW.plusHours(1).plusSeconds(1))).isEqualTo(DiscountCodeStatus.Expired);
    }

    @Test
    void isExhaustedOnlyWithLimit() {
        // when / then
        assertThat(code(true, 0, 1000, null, null).isExhausted()).isFalse();
        assertThat(code(true, 2, 1, null, null).isExhausted()).isFalse();
        assertThat(code(true, 2, 2, null, null).isExhausted()).isTrue();
        assertThat(code(true, 1, 3, null, null).isExhausted()).isTrue();
    }

    private static DiscountCode code(boolean active, int maxUses, int usedCount, LocalDateTime validFrom,
                                     LocalDateTime validTo) {
        DiscountCode code = new DiscountCode();
        code.setActive(active);
        code.setMaxUses(maxUses);
        code.setUsedCount(usedCount);
        code.setValidFrom(validFrom);
        code.setValidTo(validTo);
        return code;
    }
}
