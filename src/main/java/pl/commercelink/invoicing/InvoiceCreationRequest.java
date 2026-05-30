package pl.commercelink.invoicing;

import pl.commercelink.documents.DocumentType;

public class InvoiceCreationRequest {

    private String storeId;
    private String orderId;
    private DocumentType documentType;
    private boolean sendEmail;

    public InvoiceCreationRequest() {
    }

    public InvoiceCreationRequest(String storeId, String orderId, DocumentType documentType, boolean sendEmail) {
        this.storeId = storeId;
        this.orderId = orderId;
        this.documentType = documentType;
        this.sendEmail = sendEmail;
    }

    public String getStoreId() {
        return storeId;
    }

    public String getOrderId() {
        return orderId;
    }

    public DocumentType getDocumentType() {
        return documentType;
    }

    public boolean isSendEmail() {
        return sendEmail;
    }
}
