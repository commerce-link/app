package pl.commercelink.web.dtos;

import pl.commercelink.orders.filters.OrderFilterCondition;
import pl.commercelink.orders.filters.OrderFilterField;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

public class OrderFilterForm {

    private String name;
    private boolean global;
    private List<String> statuses = new LinkedList<>();
    private List<String> shipmentTypes = new LinkedList<>();
    private List<String> paymentSources = new LinkedList<>();
    private List<String> shippingDue = new LinkedList<>();
    private String sourceNames;
    private String postalCodePrefixes;

    public List<OrderFilterCondition> toConditions() {
        List<OrderFilterCondition> conditions = new LinkedList<>();
        addCondition(conditions, OrderFilterField.Status, statuses);
        addCondition(conditions, OrderFilterField.ShipmentType, shipmentTypes);
        addCondition(conditions, OrderFilterField.PaymentSource, paymentSources);
        addCondition(conditions, OrderFilterField.ShippingDue, shippingDue);
        addCondition(conditions, OrderFilterField.SourceName, split(sourceNames));
        addCondition(conditions, OrderFilterField.ShippingPostalCode, split(postalCodePrefixes));
        return conditions;
    }

    private static void addCondition(List<OrderFilterCondition> conditions, OrderFilterField field, List<String> values) {
        List<String> cleaned = values == null ? List.of() : values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .toList();
        if (!cleaned.isEmpty()) {
            conditions.add(new OrderFilterCondition(field, cleaned));
        }
    }

    private static List<String> split(String value) {
        return value == null ? List.of() : Arrays.stream(value.split(",")).toList();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isGlobal() {
        return global;
    }

    public void setGlobal(boolean global) {
        this.global = global;
    }

    public List<String> getStatuses() {
        return statuses;
    }

    public void setStatuses(List<String> statuses) {
        this.statuses = statuses;
    }

    public List<String> getShipmentTypes() {
        return shipmentTypes;
    }

    public void setShipmentTypes(List<String> shipmentTypes) {
        this.shipmentTypes = shipmentTypes;
    }

    public List<String> getPaymentSources() {
        return paymentSources;
    }

    public void setPaymentSources(List<String> paymentSources) {
        this.paymentSources = paymentSources;
    }

    public List<String> getShippingDue() {
        return shippingDue;
    }

    public void setShippingDue(List<String> shippingDue) {
        this.shippingDue = shippingDue;
    }

    public String getSourceNames() {
        return sourceNames;
    }

    public void setSourceNames(String sourceNames) {
        this.sourceNames = sourceNames;
    }

    public String getPostalCodePrefixes() {
        return postalCodePrefixes;
    }

    public void setPostalCodePrefixes(String postalCodePrefixes) {
        this.postalCodePrefixes = postalCodePrefixes;
    }
}
