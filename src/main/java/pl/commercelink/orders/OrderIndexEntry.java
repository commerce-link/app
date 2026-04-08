package pl.commercelink.orders;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIgnore;
import pl.commercelink.orders.fulfilment.FulfilmentType;

import java.time.LocalDateTime;

public class OrderIndexEntry {
    private String storeId;
    private String orderId;
    private String email;
    private LocalDateTime orderedAt;
    private OrderStatus status;
    private FulfilmentType fulfilmentType;

    public OrderIndexEntry() {
    }

    public OrderIndexEntry(String storeId, String orderId, String email, LocalDateTime orderedAt, OrderStatus status, FulfilmentType fulfilmentType1) {
        this.storeId = storeId;
        this.orderId = orderId;
        this.email = email;
        this.orderedAt = orderedAt;
        this.status = status;
        this.fulfilmentType = fulfilmentType1;
    }

    public String getStoreId() {
        return storeId;
    }

    public String getOrderId() {
        return orderId;
    }

    public LocalDateTime getOrderedAt() {
        return orderedAt;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public String getEmail() {
        return email;
    }

    public FulfilmentType getFulfilmentType() {
        return fulfilmentType;
    }

    @DynamoDBIgnore
    public static OrderIndexEntry fromOrder(Order order) {
        return new OrderIndexEntry(order.getStoreId(), order.getOrderId(), order.getEmail(), order.getOrderedAt(), order.getStatus(), order.getFulfilmentType());
    }
}
