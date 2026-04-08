package pl.commercelink.invoicing;

import com.fasterxml.jackson.annotation.JsonProperty;
import pl.commercelink.starter.email.EmailAttachmentBuilder;
import pl.commercelink.starter.email.EmailNotification;
import software.amazon.awssdk.services.sesv2.model.Attachment;

public class InvoiceEmailNotification extends EmailNotification {

    @JsonProperty("orderId")
    private String orderId;
    private byte[] pdfBytes;
    @JsonProperty("invoiceNumber")
    private String invoiceNumber;

    public InvoiceEmailNotification(String recipientEmail, String recipientName, String orderId, byte[] pdfBytes, String invoiceNumber) {
        super(recipientEmail, recipientName);
        this.orderId = orderId;
        this.invoiceNumber = invoiceNumber;
        this.pdfBytes = pdfBytes;
    }

    public String getOrderId() {
        return orderId;
    }

    public byte[] getPdfBytes() {
        return pdfBytes;
    }

    public String getInvoiceNumber() {
        return invoiceNumber;
    }

    @Override
    public Attachment createAttachment() {
        if (pdfBytes == null) {
            return null;
        }
        return EmailAttachmentBuilder.createAttachmentFromBytes(pdfBytes, "Invoice-" + invoiceNumber + ".pdf");
    }
}