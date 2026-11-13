package pl.commercelink.discounts;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import pl.commercelink.baskets.Basket;
import pl.commercelink.checkout.CheckoutError;
import pl.commercelink.checkout.CheckoutException;
import pl.commercelink.products.information.StorePimAliases;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DiscountService {

    private final DiscountCodesRepository discountCodesRepository;
    private final StorePimAliases aliases;

    public void apply(Basket basket, String code, String paymentProvider) {
        if (StringUtils.isNotBlank(code)) {
            applyCode(basket, code, paymentProvider);
        }
    }

    private void applyCode(Basket basket, String code, String paymentProvider) {
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

        BigDecimal minimum = money(discountCode.getMinOrderValue());
        if (money(basket.getTotalPrice()).compareTo(minimum) < 0) {
            throw new CheckoutException(CheckoutError.DiscountCodeMinimumNotReached,
                    detail(basket, normalized, "needs a basket of " + minimum), minimum);
        }

        DiscountCalculation calculation = DiscountCalculator.calculate(discountCode, basket.getEffectiveBasketItems(),
                id -> aliases.aliasesOf(basket.getStoreId(), id));
        if (calculation.lines().isEmpty()) {
            throw refusal(CheckoutError.DiscountCodeNotApplicable, basket, normalized, "covers nothing in the basket");
        }

        calculation.applyPrices();
        basket.setDiscountCode(discountCode.getCode());
        basket.setDiscountAmount(calculation.amount().doubleValue());
    }

    public void redeem(String storeId, String code) {
        try {
            Optional<DiscountCode> redeemed = discountCodesRepository.redeem(storeId, code);
            if (redeemed.isEmpty()) {
                log.error("Discount code {} of store {} was used in an order but is gone, its use is not counted", code, storeId);
            } else if (redeemed.get().isOverused()) {
                log.error("Discount code {} of store {} was used {} times, over its limit of {}",
                        code, storeId, redeemed.get().getUsedCount(), redeemed.get().getMaxUses());
            }
        } catch (RuntimeException e) {
            log.error("Use of discount code {} of store {} could not be counted", code, storeId, e);
        }
    }

    private static BigDecimal money(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    private static CheckoutException refusal(CheckoutError error, Basket basket, String code, String reason) {
        return new CheckoutException(error, detail(basket, code, reason));
    }

    private static String detail(Basket basket, String code, String reason) {
        return "discount code " + code + " of store " + basket.getStoreId() + " " + reason;
    }
}
