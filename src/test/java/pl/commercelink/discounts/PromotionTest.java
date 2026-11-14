package pl.commercelink.discounts;

import org.junit.jupiter.api.Test;
import pl.commercelink.baskets.Basket;
import pl.commercelink.baskets.BasketItem;

import java.time.LocalDateTime;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

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

    @Test
    void withoutConditionAndMinimumEveryBasketMeetsIt() {
        // given
        Promotion promotion = new Promotion();

        // when / then
        assertThat(promotion.isMetBy(basket(gpu("pim-1", 1, 100.00)), Set::of)).isTrue();
    }

    @Test
    void conditionCountsTheUnitsOfEveryMatchingItem() {
        // given
        Basket basket = basket(gpu("pim-1", 2, 100.00), gpu("pim-2", 1, 100.00), ram(50.00));

        // when / then
        assertThat(condition(3).isMetBy(basket, Set::of)).isTrue();
        assertThat(condition(4).isMetBy(basket, Set::of)).isFalse();
    }

    @Test
    void conditionCountsNeitherServicesNorVariantsLeftOut() {
        // given
        BasketItem service = gpu("srv-1", 5, 100.00);
        service.setService(true);
        BasketItem skippedVariant = gpu("pim-2", 5, 100.00);
        skippedVariant.setVariantGroupId("g1");
        BasketItem chosenVariant = gpu("pim-3", 1, 120.00);
        chosenVariant.setVariantGroupId("g1");
        chosenVariant.setVariantSelected(true);
        Basket basket = basket(gpu("pim-1", 1, 100.00), service, skippedVariant, chosenVariant);

        // when / then
        assertThat(condition(2).isMetBy(basket, Set::of)).isTrue();
        assertThat(condition(3).isMetBy(basket, Set::of)).isFalse();
    }

    @Test
    void productConditionCountsTheCloneOfTheSource() {
        // given
        Promotion promotion = new Promotion();
        promotion.setConditionTarget(DiscountTarget.Product);
        promotion.setConditionValues(List.of(new TargetValue("pim-source", "GPU")));
        promotion.setConditionQuantity(1);
        Basket basket = basket(gpu("pim-clone", 1, 100.00));

        // when / then
        assertThat(promotion.isMetBy(basket, id -> Set.of(id, "pim-source"))).isTrue();
        assertThat(promotion.isMetBy(basket, Set::of)).isFalse();
    }

    @Test
    void minimumComparesTheWholeBasketBeforeDiscount() {
        // given
        Promotion promotion = new Promotion();
        promotion.setMinOrderValue(300.00);

        // when / then
        assertThat(promotion.isMetBy(basket(gpu("pim-1", 2, 100.00), ram(100.00)), Set::of)).isTrue();
        assertThat(promotion.isMetBy(basket(gpu("pim-1", 2, 100.00), ram(99.99)), Set::of)).isFalse();
    }

    private static Promotion condition(int quantity) {
        Promotion promotion = new Promotion();
        promotion.setConditionTarget(DiscountTarget.Category);
        promotion.setConditionValues(List.of(new TargetValue("Karty graficzne", "Karty graficzne")));
        promotion.setConditionQuantity(quantity);
        return promotion;
    }

    private static Basket basket(BasketItem... items) {
        Basket basket = new Basket();
        basket.setStoreId("store-1");
        basket.setBasketItems(new LinkedList<>(List.of(items)));
        return basket;
    }

    private static BasketItem gpu(String pimId, long qty, double unitPrice) {
        return new BasketItem(pimId, "Karta " + pimId, "MFN-" + pimId, "Karty graficzne", unitPrice, 0, qty, "catalog-1", 2, false);
    }

    private static BasketItem ram(double unitPrice) {
        return new BasketItem("pim-ram", "Pamięć RAM", "MFN-RAM", "Pamięci", unitPrice, 0, 1, "catalog-1", 2, false);
    }

    private static Promotion promotion(boolean active, LocalDateTime validFrom, LocalDateTime validTo) {
        Promotion promotion = new Promotion();
        promotion.setActive(active);
        promotion.setValidFrom(validFrom);
        promotion.setValidTo(validTo);
        return promotion;
    }
}
