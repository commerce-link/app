package pl.commercelink.discounts;

import pl.commercelink.baskets.Basket;

record AppliedDiscount(String discountCode, DiscountCalculation calculation) {

    static AppliedDiscount of(DiscountCode code, DiscountCalculation calculation) {
        return new AppliedDiscount(code.getCode(), calculation);
    }

    static AppliedDiscount ofPromotions(DiscountCalculation calculation) {
        return new AppliedDiscount(null, calculation);
    }

    boolean coversAnything() {
        return !calculation.lines().isEmpty();
    }

    void applyTo(Basket basket) {
        calculation.applyPrices();
        basket.setDiscountCode(discountCode);
        basket.setDiscountAmount(calculation.amount().doubleValue());
    }
}
