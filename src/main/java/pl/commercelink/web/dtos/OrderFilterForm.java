package pl.commercelink.web.dtos;

import pl.commercelink.orders.filters.OrderFilterField;

import java.util.LinkedList;
import java.util.List;

public class OrderFilterForm {

    private String label;
    private boolean sharedWithStore;
    private String status;
    private String shipmentType;
    private String paymentSource;
    private String shippingDue;
    private String sourceName;
    private String shippingPostalCode;

    public List<String> toConditions() {
        List<String> conditions = new LinkedList<>();
        add(conditions, OrderFilterField.Status, status);
        add(conditions, OrderFilterField.ShipmentType, shipmentType);
        add(conditions, OrderFilterField.PaymentSource, paymentSource);
        add(conditions, OrderFilterField.ShippingDue, shippingDue);
        add(conditions, OrderFilterField.SourceName, sourceName);
        add(conditions, OrderFilterField.ShippingPostalCode, shippingPostalCode);
        return conditions;
    }

    private static void add(List<String> conditions, OrderFilterField field, String value) {
        if (value != null && !value.isBlank()) {
            conditions.add(field.name() + "=" + value.trim());
        }
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public boolean isSharedWithStore() {
        return sharedWithStore;
    }

    public void setSharedWithStore(boolean sharedWithStore) {
        this.sharedWithStore = sharedWithStore;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getShipmentType() {
        return shipmentType;
    }

    public void setShipmentType(String shipmentType) {
        this.shipmentType = shipmentType;
    }

    public String getPaymentSource() {
        return paymentSource;
    }

    public void setPaymentSource(String paymentSource) {
        this.paymentSource = paymentSource;
    }

    public String getShippingDue() {
        return shippingDue;
    }

    public void setShippingDue(String shippingDue) {
        this.shippingDue = shippingDue;
    }

    public String getSourceName() {
        return sourceName;
    }

    public void setSourceName(String sourceName) {
        this.sourceName = sourceName;
    }

    public String getShippingPostalCode() {
        return shippingPostalCode;
    }

    public void setShippingPostalCode(String shippingPostalCode) {
        this.shippingPostalCode = shippingPostalCode;
    }
}
