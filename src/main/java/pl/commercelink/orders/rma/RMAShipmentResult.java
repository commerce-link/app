package pl.commercelink.orders.rma;

import pl.commercelink.orders.Shipment;
import pl.commercelink.starter.util.OperationResult;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class RMAShipmentResult {

    private final OperationResult<List<Shipment>> shipments;
    private final List<String> trackingUrls;

    public RMAShipmentResult(OperationResult<List<Shipment>> shipments) {
        this.shipments = shipments;
        this.trackingUrls = shipments.isSuccess() ? shipments.getPayload().stream()
                .map(Shipment::getTrackingUrl)
                .collect(Collectors.toList()) : Collections.emptyList();
    }

    public OperationResult<List<Shipment>> getShipments() {
        return shipments;
    }

    public List<String> getTrackingUrls() {
        return trackingUrls;
    }
}
