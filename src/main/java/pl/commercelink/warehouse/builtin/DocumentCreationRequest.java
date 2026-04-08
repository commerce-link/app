package pl.commercelink.warehouse.builtin;

import pl.commercelink.documents.DocumentReason;
import pl.commercelink.documents.DocumentType;

import java.util.List;

public class DocumentCreationRequest {

    // always required
    private final DocumentType type;
    private final String storeId;
    private final IssuerDetails issuer;
    private final String warehouseId;
    private final String createdBy;
    private final DocumentReason reason;
    private final List<DocumentLineItem> items;

    // optional
    private final CounterpartyDetails counterparty;
    private final DeliveryAddress deliveryAddress;
    private final String deliveryId;
    private final String rmaId;
    private final String orderId;
    private final String note;

    private DocumentCreationRequest(Builder builder) {
        this.type = builder.type;
        this.storeId = builder.storeId;
        this.issuer = builder.issuer;
        this.warehouseId = builder.warehouseId;
        this.createdBy = builder.createdBy;
        this.reason = builder.reason;
        this.items = builder.items;
        this.counterparty = builder.counterparty;
        this.deliveryAddress = builder.deliveryAddress;
        this.deliveryId = builder.deliveryId;
        this.rmaId = builder.rmaId;
        this.orderId = builder.orderId;
        this.note = builder.note;
    }

    public static Builder builder(DocumentType type) {
        return new Builder(type);
    }

    public DocumentType getType() {
        return type;
    }

    public String getStoreId() {
        return storeId;
    }

    public IssuerDetails getIssuer() {
        return issuer;
    }

    public String getWarehouseId() {
        return warehouseId;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public DocumentReason getReason() {
        return reason;
    }

    public List<DocumentLineItem> getItems() {
        return items;
    }

    public CounterpartyDetails getCounterparty() {
        return counterparty;
    }

    public DeliveryAddress getDeliveryAddress() {
        return deliveryAddress;
    }

    public String getDeliveryId() {
        return deliveryId;
    }

    public String getRmaId() {
        return rmaId;
    }

    public String getOrderId() {
        return orderId;
    }

    public String getNote() {
        return note;
    }

    public boolean hasDeliveryId() {
        return deliveryId != null && !deliveryId.isEmpty();
    }

    public boolean hasRmaId() {
        return rmaId != null && !rmaId.isEmpty();
    }

    public boolean hasOrderId() {
        return orderId != null && !orderId.isEmpty();
    }

    public static class Builder {
        private final DocumentType type;
        private String storeId;
        private IssuerDetails issuer;
        private String warehouseId;
        private String createdBy;
        private DocumentReason reason;
        private List<DocumentLineItem> items;
        private CounterpartyDetails counterparty;
        private DeliveryAddress deliveryAddress;
        private String deliveryId;
        private String rmaId;
        private String orderId;
        private String note;

        private Builder(DocumentType type) {
            this.type = type;
        }

        public Builder storeId(String storeId) {
            this.storeId = storeId;
            return this;
        }

        public Builder issuer(IssuerDetails issuer) {
            this.issuer = issuer;
            return this;
        }

        public Builder warehouseId(String warehouseId) {
            this.warehouseId = warehouseId;
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

        public Builder items(List<DocumentLineItem> items) {
            this.items = items;
            return this;
        }

        public Builder counterparty(CounterpartyDetails counterparty) {
            this.counterparty = counterparty;
            return this;
        }

        public Builder deliveryAddress(DeliveryAddress deliveryAddress) {
            this.deliveryAddress = deliveryAddress;
            return this;
        }

        public Builder deliveryId(String deliveryId) {
            this.deliveryId = deliveryId;
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

        public Builder note(String note) {
            this.note = note;
            return this;
        }

        public DocumentCreationRequest build() {
            return new DocumentCreationRequest(this);
        }
    }
}
