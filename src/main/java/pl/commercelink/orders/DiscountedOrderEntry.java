package pl.commercelink.orders;

import java.time.LocalDateTime;

/** One entry of DiscountCodeIndex (projection: email, totalPrice, discountAmount, status). */
public record DiscountedOrderEntry(String orderId, LocalDateTime orderedAt, String email, double totalPrice,
                                   double discountAmount, OrderStatus status) {

    /** What the order sold for: a cancelled order keeps its totalPrice as a record but sold nothing (Order#getAmountDue). */
    public double soldValue() {
        return status == OrderStatus.Cancelled ? 0 : totalPrice;
    }
}
