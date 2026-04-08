package pl.commercelink.web.dtos;

import pl.commercelink.orders.BillingDetails;
import pl.commercelink.orders.PaymentSource;
import pl.commercelink.orders.ShipmentType;
import pl.commercelink.orders.ShippingDetails;
import pl.commercelink.orders.imports.OrderReferenceType;

public class ClientDataDto {

    private String orderReference;
    private OrderReferenceType orderReferenceType;
    private ShipmentType shipmentType;
    private BillingDetails billingDetails;
    private ShippingDetails shippingDetails;
    private PaymentSource paymentSource;

    public String getOrderReference() {
        return orderReference;
    }

    public void setOrderReference(String orderReference) {
        this.orderReference = orderReference;
    }

    public OrderReferenceType getOrderReferenceType() {
        return orderReferenceType;
    }

    public void setOrderReferenceType(OrderReferenceType orderReferenceType) {
        this.orderReferenceType = orderReferenceType;
    }

    public ShipmentType getShipmentType() {
        return shipmentType;
    }

    public void setShipmentType(ShipmentType shipmentType) {
        this.shipmentType = shipmentType;
    }

    public BillingDetails getBillingDetails() {
        return billingDetails;
    }

    public void setBillingDetails(BillingDetails billingDetails) {
        this.billingDetails = billingDetails;
    }

    public ShippingDetails getShippingDetails() {
        return shippingDetails;
    }

    public void setShippingDetails(ShippingDetails shippingDetails) {
        this.shippingDetails = shippingDetails;
    }

    public PaymentSource getPaymentSource() {
        return paymentSource;
    }

    public void setPaymentSource(PaymentSource paymentSource) {
        this.paymentSource = paymentSource;
    }
}
