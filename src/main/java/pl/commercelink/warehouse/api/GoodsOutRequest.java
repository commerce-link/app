package pl.commercelink.warehouse.api;

import pl.commercelink.documents.DocumentReason;
import pl.commercelink.invoicing.api.BillingParty;
import pl.commercelink.orders.ShippingDetails;

import java.util.List;

public class GoodsOutRequest {
    private final BillingParty issuer;
    private final BillingParty counterparty;
    private final ShippingDetails deliveryAddress;
    private final String storeId;
    private final String warehouseId;
    private final String orderId;
    private final List<GoodsOutItem> items;
    private final String createdBy;
    private final DocumentReason reason;

    private GoodsOutRequest(Builder builder) {
        this.storeId = builder.storeId;
        this.issuer = builder.issuer;
        this.counterparty = builder.counterparty;
        this.deliveryAddress = builder.deliveryAddress;
        this.warehouseId = builder.warehouseId;
        this.orderId = builder.orderId;
        this.items = builder.items;
        this.createdBy = builder.createdBy;
        this.reason = builder.reason;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getStoreId() {
        return storeId;
    }

    public BillingParty getIssuer() {
        return issuer;
    }

    public BillingParty getCounterparty() {
        return counterparty;
    }

    public ShippingDetails getDeliveryAddress() {
        return deliveryAddress;
    }

    public String getWarehouseId() {
        return warehouseId;
    }

    public String getOrderId() {
        return orderId;
    }

    public List<GoodsOutItem> getItems() {
        return items;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public DocumentReason getReason() {
        return reason;
    }

    public static class Builder {
        public String storeId;
        private BillingParty issuer;
        private BillingParty counterparty;
        private ShippingDetails deliveryAddress;
        private String warehouseId;
        private String orderId;
        private List<GoodsOutItem> items;
        private String createdBy;
        private DocumentReason reason;

        public Builder issuer(BillingParty issuer) {
            this.issuer = issuer;
            return this;
        }

        public Builder counterparty(BillingParty counterparty) {
            this.counterparty = counterparty;
            return this;
        }

        public Builder deliveryAddress(ShippingDetails deliveryAddress) {
            this.deliveryAddress = deliveryAddress;
            return this;
        }

        public Builder warehouseId(String warehouseId) {
            this.warehouseId = warehouseId;
            return this;
        }

        public Builder orderId(String orderId) {
            this.orderId = orderId;
            return this;
        }

        public Builder storeId(String storeId) {
            this.storeId = storeId;
            return this;
        }

        public Builder items(List<GoodsOutItem> items) {
            this.items = items;
            return this;
        }

        public Builder createdBy(String createdBy) {
            this.createdBy = createdBy;
            return this;
        }

        public Builder reason(DocumentReason reason) {
            this.reason = reason;
            return this;
        }

        public GoodsOutRequest build() {
            return new GoodsOutRequest(this);
        }
    }
}
