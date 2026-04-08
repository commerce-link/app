package pl.commercelink.orders.rma;

import pl.commercelink.orders.ShippingDetails;

public class RMAShipmentRequest {

    private String rmaId;
    private String packageTemplateId;
    private ShippingDetails customerAddress;
    private double insuranceValue;

    public RMAShipmentRequest() {
    }

    public RMAShipmentRequest(String rmaId, String packageTemplateId, ShippingDetails customerAddress, double insuranceValue) {
        this.rmaId = rmaId;
        this.packageTemplateId = packageTemplateId;
        this.customerAddress = customerAddress;
        this.insuranceValue = insuranceValue;
    }

    public String getRmaId() {
        return rmaId;
    }

    public void setRmaId(String rmaId) {
        this.rmaId = rmaId;
    }

    public String getPackageTemplateId() {
        return packageTemplateId;
    }

    public void setPackageTemplateId(String packageTemplateId) {
        this.packageTemplateId = packageTemplateId;
    }

    public ShippingDetails getCustomerAddress() {
        return customerAddress;
    }

    public void setCustomerAddress(ShippingDetails customerAddress) {
        this.customerAddress = customerAddress;
    }

    public double getInsuranceValue() {
        return insuranceValue;
    }

    public void setInsuranceValue(double insuranceValue) {
        this.insuranceValue = insuranceValue;
    }
}
