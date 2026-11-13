package pl.commercelink.discounts;

import pl.commercelink.baskets.BasketItem;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.IntStream;

public final class DiscountCalculator {

    private static final BigDecimal ONE_GROSZ = new BigDecimal("0.01");
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private DiscountCalculator() {
    }

    static DiscountCalculation calculate(Discount discount, List<BasketItem> items,
                                         Function<String, Set<String>> productAliases) {
        List<BasketItem> covered = items.stream().filter(i -> discount.covers(i, productAliases)).filter(i -> i.getQty() > 0).toList();
        List<BigDecimal> prices = discount.getType() == DiscountType.Amount ? amount(discount, covered) : percentage(discount, covered);
        List<DiscountedLine> lines = IntStream.range(0, covered.size())
                .filter(i -> prices.get(i).compareTo(price(covered.get(i))) < 0)
                .mapToObj(i -> new DiscountedLine(covered.get(i), prices.get(i).doubleValue()))
                .toList();
        return new DiscountCalculation(lines, amountOf(lines));
    }

    private static BigDecimal amountOf(List<DiscountedLine> lines) {
        return lines.stream()
                .map(l -> price(l.item()).subtract(BigDecimal.valueOf(l.unitPrice())).multiply(BigDecimal.valueOf(l.item().getQty())))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private static List<BigDecimal> percentage(Discount discount, List<BasketItem> covered) {
        BigDecimal factor = BigDecimal.ONE.subtract(BigDecimal.valueOf(discount.getValue()).divide(HUNDRED));
        return covered.stream()
                .map(i -> price(i).multiply(factor).setScale(2, RoundingMode.HALF_UP).max(ONE_GROSZ))
                .toList();
    }

    private static List<BigDecimal> amount(Discount discount, List<BasketItem> covered) {
        BigDecimal value = BigDecimal.valueOf(discount.getValue()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal coveredValue = covered.stream().map(DiscountCalculator::lineValue).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (coveredValue.signum() <= 0) {
            return covered.stream().map(DiscountCalculator::price).toList();
        }

        List<BigDecimal> perUnit = new ArrayList<>();
        for (BasketItem item : covered) {
            perUnit.add(value.multiply(price(item)).divide(coveredValue, 2, RoundingMode.DOWN).min(room(item)));
        }

        BigDecimal given = IntStream.range(0, covered.size())
                .mapToObj(i -> perUnit.get(i).multiply(BigDecimal.valueOf(covered.get(i).getQty())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal remainder = value.subtract(given);
        int largest = largestLine(covered);
        if (remainder.signum() > 0) {
            BasketItem item = covered.get(largest);
            BigDecimal extra = remainder.divide(BigDecimal.valueOf(item.getQty()), 2, RoundingMode.DOWN)
                    .min(room(item).subtract(perUnit.get(largest)));
            perUnit.set(largest, perUnit.get(largest).add(extra));
        }

        return IntStream.range(0, covered.size())
                .mapToObj(i -> price(covered.get(i)).subtract(perUnit.get(i)))
                .toList();
    }

    private static int largestLine(List<BasketItem> covered) {
        int largest = 0;
        for (int i = 1; i < covered.size(); i++) {
            if (lineValue(covered.get(i)).compareTo(lineValue(covered.get(largest))) > 0) {
                largest = i;
            }
        }
        return largest;
    }

    private static BigDecimal room(BasketItem item) {
        return price(item).subtract(ONE_GROSZ).max(BigDecimal.ZERO);
    }

    private static BigDecimal lineValue(BasketItem item) {
        return price(item).multiply(BigDecimal.valueOf(item.getQty()));
    }

    private static BigDecimal price(BasketItem item) {
        return BigDecimal.valueOf(item.getUnitPrice());
    }
}
