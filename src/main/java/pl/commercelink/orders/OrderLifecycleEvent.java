package pl.commercelink.orders;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class OrderLifecycleEvent {

    private String storeId;
    private String orderId;
    private OrderLifecycleEventType type;
    private String externalOrderId;
    private String marketplace;
    private MarketplaceReturnAction returnAction;

    public OrderLifecycleEvent(String storeId, String orderId, OrderLifecycleEventType type) {
        this(storeId, orderId, type, null, null);
    }

    public OrderLifecycleEvent(String storeId, String orderId, OrderLifecycleEventType type,
                               String externalOrderId, String marketplace) {
        this(storeId, orderId, type, externalOrderId, marketplace, null);
    }

    public OrderLifecycleEvent(String storeId, String orderId, OrderLifecycleEventType type,
                               String externalOrderId, String marketplace, MarketplaceReturnAction returnAction) {
        this.storeId = storeId;
        this.orderId = orderId;
        this.type = type;
        this.externalOrderId = externalOrderId;
        this.marketplace = marketplace;
        this.returnAction = returnAction;
    }
}
