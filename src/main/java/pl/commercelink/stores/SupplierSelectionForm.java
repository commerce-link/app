package pl.commercelink.stores;

public class SupplierSelectionForm {

    private String supplierName;
    private boolean enabled;
    private ConnectionMode mode = ConnectionMode.GLOBAL;

    public SupplierSelectionForm() {
    }

    public SupplierSelectionForm(String supplierName, boolean enabled, ConnectionMode mode) {
        this.supplierName = supplierName;
        this.enabled = enabled;
        this.mode = mode;
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
}
