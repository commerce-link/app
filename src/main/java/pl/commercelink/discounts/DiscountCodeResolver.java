package pl.commercelink.discounts;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.commercelink.baskets.Basket;
import pl.commercelink.checkout.CheckoutError;
import pl.commercelink.checkout.CheckoutException;
import pl.commercelink.products.information.StorePimAliases;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class DiscountCodeResolver {

    private final DiscountCodesRepository discountCodesRepository;
    private final StorePimAliases aliases;

    AppliedDiscount resolve(Basket basket, String code, String paymentProvider) {
        String normalized = DiscountCode.normalize(code);
        DiscountCode discountCode = discountCodesRepository.find(basket.getStoreId(), normalized)
                .orElseThrow(() -> refusal(CheckoutError.DiscountCodeNotFound, basket, normalized, "does not exist"));

        CheckoutError statusError = switch (discountCode.statusAt(LocalDateTime.now(DiscountCode.ZONE))) {
            case Disabled -> CheckoutError.DiscountCodeInactive;
            case Exhausted -> CheckoutError.DiscountCodeExhausted;
            case Expired -> CheckoutError.DiscountCodeExpired;
            case Scheduled -> CheckoutError.DiscountCodeNotYetValid;
            case Active -> null;
        };
        if (statusError != null) {
            throw refusal(statusError, basket, normalized, "is not active");
        }

        if (discountCode.getPaymentProvider() != null && !discountCode.getPaymentProvider().equals(paymentProvider)) {
            throw refusal(CheckoutError.DiscountCodePaymentMethodMismatch, basket, normalized,
                    "works only with " + discountCode.getPaymentProvider() + ", not " + paymentProvider);
        }

        if (!discountCode.isMinimumReachedBy(basket)) {
            throw new CheckoutException(CheckoutError.DiscountCodeMinimumNotReached,
                    detail(basket, normalized, "needs a basket of " + discountCode.minimum()), discountCode.minimum());
        }

        AppliedDiscount applied = AppliedDiscount.of(discountCode, DiscountCalculator.calculate(discountCode,
                basket.getEffectiveBasketItems(), id -> aliases.aliasesOf(basket.getStoreId(), id)));
        if (!applied.coversAnything()) {
            throw refusal(CheckoutError.DiscountCodeNotApplicable, basket, normalized, "covers nothing in the basket");
        }
        return applied;
    }

    private static CheckoutException refusal(CheckoutError error, Basket basket, String code, String reason) {
        return new CheckoutException(error, detail(basket, code, reason));
    }

    private static String detail(Basket basket, String code, String reason) {
        return "discount code " + code + " of store " + basket.getStoreId() + " " + reason;
    }
}
