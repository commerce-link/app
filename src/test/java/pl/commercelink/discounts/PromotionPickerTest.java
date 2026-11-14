package pl.commercelink.discounts;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.baskets.Basket;
import pl.commercelink.baskets.BasketItem;
import pl.commercelink.pim.ScopedPimCatalog;
import pl.commercelink.products.information.StorePimAliases;

import java.time.LocalDateTime;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PromotionPickerTest {

    private static final String STORE_ID = "store-1";

    @Test
    void amountPromotionWinsAnItemWhereItGivesMoreThanAPercentage() {
        // given
        Promotion fivePercentOnEverything = tenPercentOn("Acme");
        fivePercentOnEverything.setValue(5);
        fivePercentOnEverything.setTarget(null);
        fivePercentOnEverything.setTargetValues(List.of());
        Promotion twentyOffGlobex = tenPercentOn("Globex");
        twentyOffGlobex.setType(DiscountType.Amount);
        twentyOffGlobex.setValue(20);
        when(promotionsRepository.findAll(STORE_ID)).thenReturn(List.of(fivePercentOnEverything, twentyOffGlobex));

        // when
        Optional<AppliedDiscount> picked = picker.pick(basket());

        // then: GPU 129.90 × 0.95 = 123.41 (6.49 × 3), RAM 30.00 from 20 zł beats 47.50 from 5%
        assertThat(picked).hasValueSatisfying(applied -> {
            assertThat(applied.calculation().lines()).extracting(l -> l.item().getName(), DiscountedLine::unitPrice)
                    .containsExactly(tuple("GPU", 123.41), tuple("RAM", 30.00));
            assertThat(applied.calculation().amount()).isEqualByComparingTo("39.47");
        });
    }

    @Test
    void twoPromotionsGivingTheSamePriceDiscountTheItemOnce() {
        // given
        Promotion acme = tenPercentOn("Acme");
        Promotion everything = tenPercentOn("Acme");
        everything.setTarget(null);
        everything.setTargetValues(List.of());
        when(promotionsRepository.findAll(STORE_ID)).thenReturn(List.of(acme, everything));

        // when
        Optional<AppliedDiscount> picked = picker.pick(basket());

        // then
        assertThat(picked).hasValueSatisfying(applied -> {
            assertThat(applied.calculation().lines()).extracting(l -> l.item().getName(), DiscountedLine::unitPrice)
                    .containsExactly(tuple("GPU", 116.91), tuple("RAM", 45.00));
            assertThat(applied.calculation().amount()).isEqualByComparingTo("43.97");
        });
    }

    @Mock
    private PromotionsRepository promotionsRepository;

    @Spy
    private StorePimAliases aliases = new StorePimAliases(ScopedPimCatalog.of());

    @InjectMocks
    private PromotionPicker picker;

    @Test
    void picksThePromotionWhoseConditionIsMet() {
        // given
        Promotion promotion = tenPercentOn("Acme");
        promotion.setConditionTarget(DiscountTarget.Brand);
        promotion.setConditionValues(List.of(new TargetValue("Acme", "Acme")));
        promotion.setConditionQuantity(3);
        when(promotionsRepository.findAll(STORE_ID)).thenReturn(List.of(promotion));

        // when
        Optional<AppliedDiscount> picked = picker.pick(basket());

        // then
        assertThat(picked).hasValueSatisfying(applied -> {
            assertThat(applied.discountCode()).isNull();
            assertThat(applied.calculation().amount()).isEqualByComparingTo("38.97");
            assertThat(applied.calculation().lines()).extracting(l -> l.item().getName(), DiscountedLine::unitPrice)
                    .containsExactly(tuple("GPU", 116.91));
        });
    }

    @Test
    void promotionsOnDifferentItemsAllApply() {
        // given
        Promotion globex = tenPercentOn("Globex");
        globex.setValue(20);
        when(promotionsRepository.findAll(STORE_ID)).thenReturn(List.of(tenPercentOn("Acme"), globex));

        // when
        Optional<AppliedDiscount> picked = picker.pick(basket());

        // then
        assertThat(picked).hasValueSatisfying(applied -> {
            assertThat(applied.calculation().amount()).isEqualByComparingTo("48.97");
            assertThat(applied.calculation().lines()).extracting(l -> l.item().getName(), DiscountedLine::unitPrice)
                    .containsExactly(tuple("GPU", 116.91), tuple("RAM", 40.00));
        });
    }

    @Test
    void itemCoveredByTwoPromotionsGetsTheLargerOfTheirDiscounts() {
        // given
        Promotion fivePercentOnEverything = tenPercentOn("Acme");
        fivePercentOnEverything.setValue(5);
        fivePercentOnEverything.setTarget(null);
        fivePercentOnEverything.setTargetValues(List.of());
        when(promotionsRepository.findAll(STORE_ID)).thenReturn(List.of(fivePercentOnEverything, tenPercentOn("Acme")));

        // when
        Optional<AppliedDiscount> picked = picker.pick(basket());

        // then
        assertThat(picked).hasValueSatisfying(applied -> {
            assertThat(applied.calculation().amount()).isEqualByComparingTo("41.47");
            assertThat(applied.calculation().lines()).extracting(l -> l.item().getName(), DiscountedLine::unitPrice)
                    .containsExactly(tuple("GPU", 116.91), tuple("RAM", 47.50));
        });
    }

    @Test
    void amountPromotionLosesItsShareOfAnItemWonByAnotherPromotion() {
        // given
        Promotion twentyOff = tenPercentOn("Acme");
        twentyOff.setType(DiscountType.Amount);
        twentyOff.setValue(20);
        twentyOff.setTarget(null);
        twentyOff.setTargetValues(List.of());
        when(promotionsRepository.findAll(STORE_ID)).thenReturn(List.of(twentyOff, tenPercentOn("Acme")));

        // when
        Optional<AppliedDiscount> picked = picker.pick(basket());

        // then
        assertThat(picked).hasValueSatisfying(applied -> {
            assertThat(applied.calculation().amount()).isEqualByComparingTo("41.24");
            assertThat(applied.calculation().lines()).extracting(l -> l.item().getName(), DiscountedLine::unitPrice)
                    .containsExactly(tuple("GPU", 116.91), tuple("RAM", 47.73));
        });
    }

    @Test
    void minimumIsCheckedOnTheBasketBeforeAnyPromotion() {
        // given
        Promotion globex = tenPercentOn("Globex");
        globex.setValue(20);
        globex.setMinOrderValue(539.70);
        when(promotionsRepository.findAll(STORE_ID)).thenReturn(List.of(tenPercentOn("Acme"), globex));

        // when
        Optional<AppliedDiscount> picked = picker.pick(basket());

        // then
        assertThat(picked).hasValueSatisfying(applied -> assertThat(applied.calculation().lines())
                .extracting(l -> l.item().getName(), DiscountedLine::unitPrice)
                .containsExactly(tuple("GPU", 116.91), tuple("RAM", 40.00)));
    }

    @Test
    void promotionWhoseConditionOrMinimumIsNotMetIsSkipped() {
        // given
        Promotion unmet = tenPercentOn("Acme");
        unmet.setValue(50);
        unmet.setConditionTarget(DiscountTarget.Brand);
        unmet.setConditionValues(List.of(new TargetValue("Acme", "Acme")));
        unmet.setConditionQuantity(4);
        Promotion belowMinimum = tenPercentOn("Acme");
        belowMinimum.setValue(40);
        belowMinimum.setMinOrderValue(1000);
        when(promotionsRepository.findAll(STORE_ID)).thenReturn(List.of(unmet, belowMinimum, tenPercentOn("Acme")));

        // when
        Optional<AppliedDiscount> picked = picker.pick(basket());

        // then
        assertThat(picked).hasValueSatisfying(applied -> assertThat(applied.calculation().lines())
                .extracting(l -> l.item().getName(), DiscountedLine::unitPrice)
                .containsExactly(tuple("GPU", 116.91)));
    }

    @Test
    void promotionsThatAreNotActiveAreSkipped() {
        // given
        Promotion disabled = tenPercentOn("Acme");
        disabled.setActive(false);
        Promotion expired = tenPercentOn("Acme");
        expired.setValidTo(LocalDateTime.of(2020, 1, 31, 23, 59, 59));
        Promotion scheduled = tenPercentOn("Acme");
        scheduled.setValidFrom(LocalDateTime.of(2099, 1, 1, 0, 0));
        when(promotionsRepository.findAll(STORE_ID)).thenReturn(List.of(disabled, expired, scheduled));

        // when / then
        assertThat(picker.pick(basket())).isEmpty();
    }

    @Test
    void promotionThatCoversNothingIsNotPicked() {
        // given
        when(promotionsRepository.findAll(STORE_ID)).thenReturn(List.of(tenPercentOn("Initech")));

        // when / then
        assertThat(picker.pick(basket())).isEmpty();
    }

    @Test
    void storeWithoutPromotionsPicksNothing() {
        // when / then
        assertThat(picker.pick(basket())).isEmpty();
    }

    private static Promotion tenPercentOn(String brand) {
        Promotion promotion = new Promotion();
        promotion.setStoreId(STORE_ID);
        promotion.setName(brand + " −10%");
        promotion.setActive(true);
        promotion.setType(DiscountType.Percentage);
        promotion.setValue(10);
        promotion.setTarget(DiscountTarget.Brand);
        promotion.setTargetValues(List.of(new TargetValue(brand, brand)));
        return promotion;
    }

    private static Basket basket() {
        BasketItem service = product("Montaż", 100.00, 1, "Acme");
        service.setService(true);
        Basket basket = new Basket();
        basket.setStoreId(STORE_ID);
        basket.setBasketItems(new LinkedList<>(List.of(
                product("GPU", 129.90, 3, "Acme"),
                product("RAM", 50.00, 1, "Globex"),
                service)));
        return basket;
    }

    private static BasketItem product(String name, double unitPrice, long qty, String brand) {
        BasketItem item = new BasketItem("pim-" + name, name, "MFN-" + name, "Podzespoły", unitPrice, 0, qty, "catalog-1", 2, false);
        item.setBrand(brand);
        return item;
    }
}
