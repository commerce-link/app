package pl.commercelink.orders.rma;

import pl.commercelink.orders.ShippingDetails;

import java.io.Serializable;

// A refused return form travels back to the customer as a flash attribute, i.e. in the HTTP session, and session
// attributes are expected to be serializable.
public class RMAReturnForm implements Serializable {

    private static final long serialVersionUID = 1L;

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
