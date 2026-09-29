package pl.commercelink.web.dtos;

import pl.commercelink.orders.filters.model.OrderFilterCondition;
import pl.commercelink.orders.filters.OrderFilterField;

import java.util.LinkedList;
import java.util.List;

public class OrderFilterForm {

    private String label;
    private boolean sharedWithStore;
    private boolean openByDefault;
    private List<String> status = List.of();
    private List<String> shipmentType = List.of();
    private List<String> paymentSource = List.of();
    private String shippingDue;
    private List<String> sourceName = List.of();
    private String shippingPostalCode;
    private String returnTo;

    public List<OrderFilterCondition> toConditions() {
        List<OrderFilterCondition> conditions = new LinkedList<>();
        status.forEach(value -> add(conditions, OrderFilterField.Status, value));
        shipmentType.forEach(value -> add(conditions, OrderFilterField.ShipmentType, value));
        paymentSource.forEach(value -> add(conditions, OrderFilterField.PaymentSource, value));
        add(conditions, OrderFilterField.ShippingDue, shippingDue);
        sourceName.forEach(value -> add(conditions, OrderFilterField.SourceName, value));
        add(conditions, OrderFilterField.ShippingPostalCode, shippingPostalCode);
        return conditions;
    }

    // an unticked field is simply absent from the POST, so the binder leaves it untouched or sets null
    private static List<String> listOf(List<String> values) {
        return values == null ? List.of() : values.stream().filter(java.util.Objects::nonNull).toList();
    }

    private static void add(List<OrderFilterCondition> conditions, OrderFilterField field, String rawValue) {
        if (!field.normalize(rawValue).isEmpty()) {
            conditions.add(OrderFilterCondition.of(field, rawValue));
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

    public boolean isOpenByDefault() {
        return openByDefault;
    }

    public void setOpenByDefault(boolean openByDefault) {
        this.openByDefault = openByDefault;
    }

    public List<String> getStatus() {
        return status;
    }

    public void setStatus(List<String> status) {
        this.status = listOf(status);
    }

    public List<String> getShipmentType() {
        return shipmentType;
    }

    public void setShipmentType(List<String> shipmentType) {
        this.shipmentType = listOf(shipmentType);
    }

    public List<String> getPaymentSource() {
        return paymentSource;
    }

    public void setPaymentSource(List<String> paymentSource) {
        this.paymentSource = listOf(paymentSource);
    }

    public String getShippingDue() {
        return shippingDue;
    }

    public void setShippingDue(String shippingDue) {
        this.shippingDue = shippingDue;
    }

    public List<String> getSourceName() {
        return sourceName;
    }

    public void setSourceName(List<String> sourceName) {
        this.sourceName = listOf(sourceName);
    }

    public String getShippingPostalCode() {
        return shippingPostalCode;
    }

    public void setShippingPostalCode(String shippingPostalCode) {
        this.shippingPostalCode = shippingPostalCode;
    }

    public String getReturnTo() {
        return returnTo;
    }

    public void setReturnTo(String returnTo) {
        this.returnTo = returnTo;
    }

}
