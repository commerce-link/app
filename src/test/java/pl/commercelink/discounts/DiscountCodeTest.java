package pl.commercelink.discounts;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import pl.commercelink.baskets.BasketItem;

import java.time.LocalDateTime;
import java.util.Arrays;

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

    @Test
    void isOverusedOnlyAboveLimit() {
        // when / then
        assertThat(code(true, 0, 1000, null, null).isOverused()).isFalse();
        assertThat(code(true, 2, 2, null, null).isOverused()).isFalse();
        assertThat(code(true, 2, 3, null, null).isOverused()).isTrue();
    }

    @Test
    void coversEveryProductWhenNoTargetValues() {
        // given
        DiscountCode code = covering(null);

        // when / then
        assertThat(code.covers(product("pim-1", "Karty graficzne", "Acme"))).isTrue();
        assertThat(code.covers(product("pim-2", "Procesory", "Globex"))).isTrue();
    }

    @ParameterizedTest
    @CsvSource({"Product, pim-1, pim-2", "Category, Karty graficzne, Procesory", "Brand, Acme, Globex"})
    void coversItemMatchingTargetValue(DiscountTarget target, String matching, String other) {
        // given
        BasketItem item = product("pim-1", "Karty graficzne", "Acme");

        // when / then
        assertThat(covering(target, matching).covers(item)).isTrue();
        assertThat(covering(target, other).covers(item)).isFalse();
    }

    @Test
    void categoryCodeCoversSameNameInEveryCatalog() {
        // given
        BasketItem fromFirstCatalog = product("pim-1", "Karty graficzne", "Acme");
        fromFirstCatalog.setCatalogId("catalog-1");
        BasketItem fromSecondCatalog = product("pim-2", "Karty graficzne", "Globex");
        fromSecondCatalog.setCatalogId("catalog-2");
        DiscountCode code = covering(DiscountTarget.Category, "Karty graficzne");

        // when / then
        assertThat(code.covers(fromFirstCatalog)).isTrue();
        assertThat(code.covers(fromSecondCatalog)).isTrue();
    }

    @Test
    void coversIgnoresSurroundingSpaces() {
        // given
        BasketItem item = product("pim-1", "Karty graficzne ", " Acme");

        // when / then
        assertThat(covering(DiscountTarget.Category, "Karty graficzne").covers(item)).isTrue();
        assertThat(covering(DiscountTarget.Brand, "Acme ").covers(item)).isTrue();
    }

    @Test
    void neverCoversServiceOrShippingItem() {
        // given
        BasketItem service = product("srv-1", "Usługi", "Acme");
        service.setService(true);
        BasketItem shipping = BasketItem.shipping(15.00);
        DiscountCode wholeBasket = covering(null);
        DiscountCode serviceCategory = covering(DiscountTarget.Category, "Usługi");

        // when / then
        assertThat(wholeBasket.covers(service)).isFalse();
        assertThat(wholeBasket.covers(shipping)).isFalse();
        assertThat(serviceCategory.covers(service)).isFalse();
    }

    private static DiscountCode covering(DiscountTarget target, String... values) {
        DiscountCode code = new DiscountCode();
        code.setTarget(target);
        code.setTargetValues(Arrays.stream(values).map(v -> new TargetValue(v, v)).toList());
        return code;
    }

    private static BasketItem product(String pimId, String category, String brand) {
        BasketItem item = new BasketItem(pimId, "Produkt " + pimId, "MFN-" + pimId, category, 100, 0, 1, "catalog-1", 2, false);
        item.setBrand(brand);
        return item;
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
