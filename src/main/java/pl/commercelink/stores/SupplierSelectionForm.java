package pl.commercelink.stores;

public class SupplierSelectionForm {

    private String supplierName;
    private boolean enabled;
    private ConnectionMode mode = ConnectionMode.GLOBAL;
    private boolean includeInPricing = true;
    private boolean includeInFulfilment = true;

    public SupplierSelectionForm() {
    }

    public SupplierSelectionForm(String supplierName, boolean enabled, ConnectionMode mode) {
        this(supplierName, enabled, mode, true, true);
    }

    public SupplierSelectionForm(String supplierName, boolean enabled, ConnectionMode mode,
                                 boolean includeInPricing, boolean includeInFulfilment) {
        this.supplierName = supplierName;
        this.enabled = enabled;
        this.mode = mode;
        this.includeInPricing = includeInPricing;
        this.includeInFulfilment = includeInFulfilment;
    }

    public String getSupplierName() {
        return supplierName;
    }

    public void setSupplierName(String supplierName) {
        this.supplierName = supplierName;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
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
}
