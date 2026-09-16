package pl.commercelink.stores;

public class SupplierSelectionForm {

    private String supplierName;
    private ConnectionMode mode = ConnectionMode.GLOBAL;
    private boolean includeInPricing = true;
    private boolean includeInFulfilment = true;
    private String externalSupplierId;
    private String feedSchedule;
    private String identity;
    private String label;
    private String billingShortcut;

    public SupplierSelectionForm() {
    }

    public SupplierSelectionForm(String supplierName, ConnectionMode mode,
                                 boolean includeInPricing, boolean includeInFulfilment) {
        this(supplierName, mode, includeInPricing, includeInFulfilment, null);
    }

    public SupplierSelectionForm(String supplierName, ConnectionMode mode,
                                 boolean includeInPricing, boolean includeInFulfilment, String feedSchedule) {
        this.supplierName = supplierName;
        this.mode = mode;
        this.includeInPricing = includeInPricing;
        this.includeInFulfilment = includeInFulfilment;
        this.feedSchedule = feedSchedule;
    }

    public String getSupplierName() {
        return supplierName;
    }

    public void setSupplierName(String supplierName) {
        this.supplierName = supplierName;
    }

    public ConnectionMode getMode() {
        return mode;
    }

    public void setMode(ConnectionMode mode) {
        this.mode = mode;
    }

    public boolean isIncludeInPricing() {
        return includeInPricing;
    }

    public void setIncludeInPricing(boolean includeInPricing) {
        this.includeInPricing = includeInPricing;
    }

    public boolean isIncludeInFulfilment() {
        return includeInFulfilment;
    }

    public void setIncludeInFulfilment(boolean includeInFulfilment) {
        this.includeInFulfilment = includeInFulfilment;
    }

    public String getExternalSupplierId() {
        return externalSupplierId;
    }

    public void setExternalSupplierId(String externalSupplierId) {
        this.externalSupplierId = externalSupplierId;
    }

    public String getFeedSchedule() {
        return feedSchedule;
    }

    public void setFeedSchedule(String feedSchedule) {
        this.feedSchedule = feedSchedule;
    }

    public String getIdentity() {
        return identity;
    }

    public void setIdentity(String identity) {
        this.identity = identity;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getBillingShortcut() {
        return billingShortcut;
    }

    public void setBillingShortcut(String billingShortcut) {
        this.billingShortcut = billingShortcut;
    }
}
