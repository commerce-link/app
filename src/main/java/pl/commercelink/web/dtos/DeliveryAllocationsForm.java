package pl.commercelink.web.dtos;

import org.springframework.format.annotation.DateTimeFormat;
import pl.commercelink.inventory.deliveries.Allocation;
import pl.commercelink.inventory.deliveries.AllocationType;
import pl.commercelink.inventory.deliveries.DropshipShipment;
import pl.commercelink.orders.ShipmentType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class DeliveryAllocationsForm {

    private String storeId;
    private String deliveryId;
    private String provider;
    private String targetDeliveryId;
    private String targetExternalDeliveryId;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate targetEstimatedDeliveryAt;
    private ShipmentType shipmentType;
    private String shipmentCarrier;
    private String shipmentTrackingNo;
    private String shipmentCollectionPointCode;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime shipmentShippedAt;
    private List<Allocation> allocations = new ArrayList<>();

    public DeliveryAllocationsForm() {}

    public DeliveryAllocationsForm(String storeId, String deliveryId, String provider, List<Allocation> allocations) {
        this.storeId = storeId;
        this.deliveryId = deliveryId;
        this.provider = provider;
        this.allocations = allocations;
    }

    public String getStoreId() {
        return storeId;
    }

    public void setStoreId(String storeId) {
        this.storeId = storeId;
    }

    public String getDeliveryId() {
        return deliveryId;
    }

    public void setDeliveryId(String deliveryId) {
        this.deliveryId = deliveryId;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getTargetDeliveryId() {
        return targetDeliveryId;
    }

    public void setTargetDeliveryId(String targetDeliveryId) {
        this.targetDeliveryId = targetDeliveryId;
    }

    public String getTargetExternalDeliveryId() {
        return targetExternalDeliveryId;
    }

    public void setTargetExternalDeliveryId(String targetExternalDeliveryId) {
        this.targetExternalDeliveryId = targetExternalDeliveryId;
    }

    public LocalDate getTargetEstimatedDeliveryAt() {
        return targetEstimatedDeliveryAt;
    }

    public void setTargetEstimatedDeliveryAt(LocalDate targetEstimatedDeliveryAt) {
        this.targetEstimatedDeliveryAt = targetEstimatedDeliveryAt;
    }

    public ShipmentType getShipmentType() {
        return shipmentType;
    }

    public void setShipmentType(ShipmentType shipmentType) {
        this.shipmentType = shipmentType;
    }

    public String getShipmentCarrier() {
        return shipmentCarrier;
    }

    public void setShipmentCarrier(String shipmentCarrier) {
        this.shipmentCarrier = shipmentCarrier;
    }

    public String getShipmentTrackingNo() {
        return shipmentTrackingNo;
    }

    public void setShipmentTrackingNo(String shipmentTrackingNo) {
        this.shipmentTrackingNo = shipmentTrackingNo;
    }

    public String getShipmentCollectionPointCode() {
        return shipmentCollectionPointCode;
    }

    public void setShipmentCollectionPointCode(String shipmentCollectionPointCode) {
        this.shipmentCollectionPointCode = shipmentCollectionPointCode;
    }

    public LocalDateTime getShipmentShippedAt() {
        return shipmentShippedAt;
    }

    public void setShipmentShippedAt(LocalDateTime shipmentShippedAt) {
        this.shipmentShippedAt = shipmentShippedAt;
    }

    public DropshipShipment toDropshipShipment() {
        return new DropshipShipment(shipmentType, shipmentCarrier, shipmentTrackingNo,
                shipmentCollectionPointCode, shipmentShippedAt);
    }

    public List<Allocation> getAllocations() {
        return allocations;
    }

    public void setAllocations(List<Allocation> allocations) {
        this.allocations = allocations;
    }

    public List<Allocation> getSelectedOrderAllocations() {
        return this.allocations.stream()
                .filter(Allocation::isSelected)
                .filter(a -> a.getType() == AllocationType.Order)
                .collect(Collectors.toList());
    }

    public List<Allocation> getSelectedWarehouseAllocations() {
        return this.allocations.stream()
                .filter(Allocation::isSelected)
                .filter(a -> a.getType() == AllocationType.Warehouse)
                .collect(Collectors.toList());
    }

    public List<Allocation> getRemainingAllocations() {
        return this.allocations.stream()
                .filter(a -> !a.isSelected())
                .collect(Collectors.toList());
    }

    public List<Allocation> getSelectedAllocations() {
        return this.allocations.stream()
                .filter(Allocation::isSelected)
                .filter(Allocation::isInAllocation)
                .collect(Collectors.toList());
    }
}
