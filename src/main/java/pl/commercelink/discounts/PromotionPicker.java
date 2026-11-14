package pl.commercelink.discounts;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.commercelink.baskets.Basket;
import pl.commercelink.baskets.BasketItem;
import pl.commercelink.products.information.StorePimAliases;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
public class PromotionPicker {

    private final PromotionsRepository promotionsRepository;
    private final StorePimAliases aliases;

    Optional<AppliedDiscount> pick(Basket basket) {
        LocalDateTime now = LocalDateTime.now(DiscountCode.ZONE);
        Function<String, Set<String>> productAliases = id -> aliases.aliasesOf(basket.getStoreId(), id);
        List<BasketItem> items = basket.getEffectiveBasketItems();
        List<DiscountedLine> lines = promotionsRepository.findAll(basket.getStoreId()).stream()
                .filter(p -> p.statusAt(now) == DiscountCodeStatus.Active)
                .filter(p -> p.isMetBy(basket, productAliases))
                .flatMap(p -> DiscountCalculator.calculate(p, items, productAliases).lines().stream())
                .toList();
        return Optional.of(AppliedDiscount.ofPromotions(DiscountCalculation.of(lowestPricePerItem(items, lines))))
                .filter(AppliedDiscount::coversAnything);
    }

    private static List<DiscountedLine> lowestPricePerItem(List<BasketItem> items, List<DiscountedLine> lines) {
        return items.stream()
                .flatMap(item -> lines.stream()
                        .filter(line -> line.item() == item)
                        .reduce((kept, next) -> next.unitPrice() < kept.unitPrice() ? next : kept)
                        .stream())
                .toList();
    }
}
