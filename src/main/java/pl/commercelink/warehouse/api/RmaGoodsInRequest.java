package pl.commercelink.warehouse.api;

import pl.commercelink.invoicing.api.BillingParty;

import java.util.List;

public class RmaGoodsInRequest {

    private final String storeId;
    private final List<GoodsReceiptItem> items;
    private final boolean itemsRequireRepair;

    private final BillingParty issuer;
    private final BillingParty counterparty;
    private final String warehouseId;
    private final String rmaId;
    private final String orderId;
    private final String createdBy;

    private RmaGoodsInRequest(Builder builder) {
        this.storeId = builder.storeId;
        this.items = builder.items;
        this.itemsRequireRepair = builder.itemsRequireRepair;
        this.issuer = builder.issuer;
        this.counterparty = builder.counterparty;
        this.warehouseId = builder.warehouseId;
        this.rmaId = builder.rmaId;
        this.orderId = builder.orderId;
        this.createdBy = builder.createdBy;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getStoreId() {
        return storeId;
    }

    public List<GoodsReceiptItem> getItems() {
        return items;
    }

    public boolean isItemsRequireRepair() {
        return itemsRequireRepair;
    }

    public BillingParty getIssuer() {
        return issuer;
    }

    public BillingParty getCounterparty() {
        return counterparty;
    }

    public String getWarehouseId() {
        return warehouseId;
    }

    public String getRmaId() {
        return rmaId;
    }

    public String getOrderId() {
        return orderId;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public boolean hasDocumentData() {
        return issuer != null && counterparty != null && warehouseId != null && rmaId != null;
    }

    public static class Builder {
        private String storeId;
        private List<GoodsReceiptItem> items;
        private boolean itemsRequireRepair;
        private BillingParty issuer;
        private BillingParty counterparty;
        private String warehouseId;
        private String rmaId;
        private String createdBy;
        private String orderId;

        public Builder storeId(String storeId) {
            this.storeId = storeId;
            return this;
        }

        public Builder items(List<GoodsReceiptItem> items) {
            this.items = items;
            return this;
        }

        public Builder itemsRequireRepair(boolean itemsRequireRepair) {
            this.itemsRequireRepair = itemsRequireRepair;
            return this;
        }

        public Builder issuer(BillingParty issuer) {
            this.issuer = issuer;
            return this;
        }

        public Builder counterparty(BillingParty counterparty) {
            this.counterparty = counterparty;
            return this;
        }

        public Builder warehouseId(String warehouseId) {
            this.warehouseId = warehouseId;
            return this;
        }

        public Builder rmaId(String rmaId) {
            this.rmaId = rmaId;
            return this;
        }

        public Builder orderId(String orderId) {
            this.orderId = orderId;
            return this;
        }

        public Builder createdBy(String createdBy) {
            this.createdBy = createdBy;
            return this;
        }

        public RmaGoodsInRequest build() {
            return new RmaGoodsInRequest(this);
        }
    }
}
