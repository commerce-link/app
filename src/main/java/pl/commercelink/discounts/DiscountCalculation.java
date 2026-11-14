package pl.commercelink.discounts;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

record DiscountCalculation(List<DiscountedLine> lines, BigDecimal amount) {

    static DiscountCalculation of(List<DiscountedLine> lines) {
        BigDecimal amount = lines.stream()
                .map(l -> BigDecimal.valueOf(l.item().getUnitPrice()).subtract(BigDecimal.valueOf(l.unitPrice()))
                        .multiply(BigDecimal.valueOf(l.item().getQty())))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        return new DiscountCalculation(lines, amount);
    }

    void applyPrices() {
        lines.forEach(l -> l.item().setUnitPrice(l.unitPrice()));
    }
}
