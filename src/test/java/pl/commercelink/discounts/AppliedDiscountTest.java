package pl.commercelink.discounts;

import org.junit.jupiter.api.Test;
import pl.commercelink.baskets.Basket;
import pl.commercelink.baskets.BasketItem;

import java.math.BigDecimal;
import java.util.LinkedList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AppliedDiscountTest {

    @Test
    void codeIsWrittenToTheBasketWithItsPricesAndAmount() {
        // given
        BasketItem gpu = gpu();
        Basket basket = basket(gpu);
        DiscountCode code = new DiscountCode();
        code.setCode("LATO-10");
        AppliedDiscount applied = AppliedDiscount.of(code, tenOff(gpu));

        // when
        applied.applyTo(basket);

        // then
        assertThat(gpu.getUnitPrice()).isEqualTo(90.00);
        assertThat(basket.getDiscountCode()).isEqualTo("LATO-10");
        assertThat(basket.getDiscountAmount()).isEqualTo(10.00);
        assertThat(basket.hasPromotions()).isFalse();
    }

    @Test
    void promotionsAreWrittenToTheBasketWithTheirPricesAndAmountWithoutACode() {
        // given
        BasketItem gpu = gpu();
        Basket basket = basket(gpu);
        AppliedDiscount applied = AppliedDiscount.ofPromotions(tenOff(gpu));

        // when
        applied.applyTo(basket);

        // then
        assertThat(gpu.getUnitPrice()).isEqualTo(90.00);
        assertThat(basket.getDiscountCode()).isNull();
        assertThat(basket.getDiscountAmount()).isEqualTo(10.00);
        assertThat(basket.hasPromotions()).isTrue();
    }

    @Test
    void calculationWithoutLinesCoversNothing() {
        // given
        AppliedDiscount empty = AppliedDiscount.ofPromotions(new DiscountCalculation(List.of(), BigDecimal.ZERO));

        // when / then
        assertThat(empty.coversAnything()).isFalse();
        assertThat(AppliedDiscount.ofPromotions(tenOff(gpu())).coversAnything()).isTrue();
    }

    private static DiscountCalculation tenOff(BasketItem item) {
        return new DiscountCalculation(List.of(new DiscountedLine(item, 90.00)), new BigDecimal("10.00"));
    }

    private static BasketItem gpu() {
        return new BasketItem("pim-GPU", "GPU", "MFN-GPU", "Karty graficzne", 100.00, 0, 1, "catalog-1", 2, false);
    }

    private static Basket basket(BasketItem... items) {
        Basket basket = new Basket();
        basket.setStoreId("store-1");
        basket.setBasketItems(new LinkedList<>(List.of(items)));
        return basket;
    }
}
