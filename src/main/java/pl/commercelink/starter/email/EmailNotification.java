package pl.commercelink.starter.email;

import com.fasterxml.jackson.annotation.JsonProperty;
import software.amazon.awssdk.services.sesv2.model.Attachment;

public class EmailNotification {

    @JsonProperty("recipientEmail")
    private String recipientEmail;
    @JsonProperty("recipientName")
    private String recipientName;

    public EmailNotification(String recipientEmail, String recipientName) {
        this.recipientEmail = recipientEmail;
        this.recipientName = recipientName;
    }

    public String getRecipientEmail() {
        return recipientEmail;
    }

    public String getRecipientName() {
        return recipientName;
    }

    public Attachment createAttachment() {
        return null;
    }
}
