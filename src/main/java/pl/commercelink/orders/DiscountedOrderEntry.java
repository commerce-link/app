package pl.commercelink.orders;

import java.time.LocalDateTime;

/** One entry of DiscountCodeIndex (projection: email, totalPrice, discountAmount, status). */
public record DiscountedOrderEntry(String orderId, LocalDateTime orderedAt, String email, double totalPrice,
                                   double discountAmount, OrderStatus status) {
}
