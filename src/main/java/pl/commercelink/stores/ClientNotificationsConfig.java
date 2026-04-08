package pl.commercelink.stores;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBDocument;
import pl.commercelink.orders.notifications.EmailNotificationType;

import java.util.HashMap;
import java.util.Map;

@DynamoDBDocument
public class ClientNotificationsConfig {

    @DynamoDBAttribute(attributeName = "senderName")
    private String senderName;
    @DynamoDBAttribute(attributeName = "replyToEmail")
    private String replyToEmail;
    @DynamoDBAttribute(attributeName = "supportedTemplates")
    private Map<String, String> supportedTemplates = new HashMap<>();

    public ClientNotificationsConfig() {
    }

    public boolean supports(EmailNotificationType type) {
        return supportedTemplates.containsKey(type.name());
    }

    public void enableNotification(EmailNotificationType type, String templateName) {
        supportedTemplates.put(type.name(), templateName);
    }

    public void disableNotification(EmailNotificationType type) {
        supportedTemplates.remove(type.name());
    }

    public String getTemplateName(EmailNotificationType type) {
        return supportedTemplates.getOrDefault(type.name(), null);
    }

    public String getSenderName() {
        return senderName;
    }

    public void setSenderName(String senderName) {
        this.senderName = senderName;
    }

    public String getReplyToEmail() {
        return replyToEmail;
    }

    public void setReplyToEmail(String replyToEmail) {
        this.replyToEmail = replyToEmail;
    }

    public Map<String, String> getSupportedTemplates() {
        return supportedTemplates;
    }

    public void setSupportedTemplates(Map<String, String> templates) {
        this.supportedTemplates = templates;
    }

}
