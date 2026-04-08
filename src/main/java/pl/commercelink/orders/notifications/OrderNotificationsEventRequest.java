package pl.commercelink.orders.notifications;

import java.time.LocalDate;

public record OrderNotificationsEventRequest(String storeId, String orderId, LocalDate oldAssemblyDate) {

    public OrderNotificationsEventRequest(String storeId, String orderId) {
        this(storeId, orderId, null);
    }
}
