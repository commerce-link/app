package pl.commercelink.warehouse.api;

import pl.commercelink.inventory.deliveries.Allocation;
import pl.commercelink.invoicing.api.BillingParty;

import java.util.List;

public class GoodsInRequest {
    private final BillingParty issuer;
    private final BillingParty counterparty;
    private final String warehouseId;
    private final String deliveryId;
    private final List<Allocation> orderAllocations;
    private final List<Allocation> warehouseAllocations;
    private final String createdBy;

    private GoodsInRequest(Builder builder) {
        this.issuer = builder.issuer;
        this.counterparty = builder.counterparty;
        this.warehouseId = builder.warehouseId;
        this.deliveryId = builder.deliveryId;
        this.orderAllocations = builder.orderAllocations;
        this.warehouseAllocations = builder.warehouseAllocations;
        this.createdBy = builder.createdBy;
    }

    public static Builder builder() {
        return new Builder();
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

    public String getDeliveryId() {
        return deliveryId;
    }

    public List<Allocation> getOrderAllocations() {
        return orderAllocations;
    }

    public List<Allocation> getWarehouseAllocations() {
        return warehouseAllocations;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public static class Builder {
        private BillingParty issuer;
        private BillingParty counterparty;
        private String warehouseId;
        private String deliveryId;
        private List<Allocation> orderAllocations;
        private List<Allocation> warehouseAllocations;
        private String createdBy;

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

        public Builder deliveryId(String deliveryId) {
            this.deliveryId = deliveryId;
            return this;
        }

        public Builder orderAllocations(List<Allocation> orderAllocations) {
            this.orderAllocations = orderAllocations;
            return this;
        }

        public Builder warehouseAllocations(List<Allocation> warehouseAllocations) {
            this.warehouseAllocations = warehouseAllocations;
            return this;
        }

        public Builder createdBy(String createdBy) {
            this.createdBy = createdBy;
            return this;
        }

        public GoodsInRequest build() {
            return new GoodsInRequest(this);
        }
    }
}
