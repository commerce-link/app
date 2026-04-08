package pl.commercelink.invoicing;

public class InvoiceCreationRequest {

    private String storeId;
    private String orderId;
    private boolean sendEmail;

    public InvoiceCreationRequest() {
    }

    public InvoiceCreationRequest(String storeId, String orderId, boolean sendEmail) {
        this.storeId = storeId;
        this.orderId = orderId;
        this.sendEmail = sendEmail;
    }

    public String getStoreId() {
        return storeId;
    }

    public String getOrderId() {
        return orderId;
    }

    public boolean isSendEmail() {
        return sendEmail;
    }
}
