package pl.commercelink.orders.rma;

import pl.commercelink.orders.ShippingDetails;

public class RMAReturnForm {

    private ShippingDetails shippingDetails;
    private String selectedPackageTemplateId;
    private String rejectionReason;

    public RMAReturnForm() {
    }

    public ShippingDetails getShippingDetails() {
        return shippingDetails;
    }

    public void setShippingDetails(ShippingDetails shippingDetails) {
        this.shippingDetails = shippingDetails;
    }

    public String getSelectedPackageTemplateId() {
        return selectedPackageTemplateId;
    }

    public void setSelectedPackageTemplateId(String selectedPackageTemplateId) {
        this.selectedPackageTemplateId = selectedPackageTemplateId;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }
}
