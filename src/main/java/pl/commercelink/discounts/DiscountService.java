package pl.commercelink.discounts;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import pl.commercelink.baskets.Basket;
import pl.commercelink.checkout.CheckoutError;
import pl.commercelink.checkout.CheckoutException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DiscountService {

    private final DiscountCodesRepository discountCodesRepository;

    public void apply(Basket basket, String code, String paymentProvider) {
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

        List<DiscountedLine> lines = DiscountCalculator.calculate(discountCode, basket.getEffectiveBasketItems());
        if (lines.isEmpty()) {
            throw refusal(CheckoutError.DiscountCodeNotApplicable, basket, normalized, "covers nothing in the basket");
        }

        BigDecimal amount = lines.stream()
                .map(l -> BigDecimal.valueOf(l.item().getUnitPrice()).subtract(BigDecimal.valueOf(l.unitPrice()))
                        .multiply(BigDecimal.valueOf(l.item().getQty())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        lines.forEach(l -> l.item().setUnitPrice(l.unitPrice()));
        basket.setDiscountCode(discountCode.getCode());
        basket.setDiscountAmount(money(amount.doubleValue()).doubleValue());
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
