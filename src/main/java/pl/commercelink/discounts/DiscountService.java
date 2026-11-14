package pl.commercelink.discounts;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import pl.commercelink.baskets.Basket;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DiscountService {

    private final DiscountCodeResolver discountCodeResolver;
    private final PromotionPicker promotionPicker;
    private final DiscountCodesRepository discountCodesRepository;

    public void apply(Basket basket, String code, String paymentProvider) {
        if (StringUtils.isNotBlank(code)) {
            discountCodeResolver.resolve(basket, code, paymentProvider).applyTo(basket);
        } else {
            promotionPicker.pick(basket).ifPresent(promotion -> promotion.applyTo(basket));
        }
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
}
