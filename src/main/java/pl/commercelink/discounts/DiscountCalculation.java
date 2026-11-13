package pl.commercelink.discounts;

import java.math.BigDecimal;
import java.util.List;

record DiscountCalculation(List<DiscountedLine> lines, BigDecimal amount) {

    void applyPrices() {
        lines.forEach(l -> l.item().setUnitPrice(l.unitPrice()));
    }
}
