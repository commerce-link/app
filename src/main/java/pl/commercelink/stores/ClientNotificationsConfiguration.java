package pl.commercelink.stores;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBDocument;
import pl.commercelink.orders.notifications.EmailNotificationType;

import java.util.HashMap;
import java.util.Map;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

@DynamoDBDocument
public class ClientNotificationsConfiguration {

    @DynamoDBAttribute(attributeName = "senderName")
    private String senderName;
    @DynamoDBAttribute(attributeName = "replyToEmail")
    private String replyToEmail;
    @DynamoDBAttribute(attributeName = "supportedTemplates")
    private Map<String, String> supportedTemplates = new HashMap<>();

    public ClientNotificationsConfiguration() {
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

    /** No customer email goes out without both: the name it is signed with and the address replies go to. */
    public boolean hasSender() {
        return isNotBlank(senderName) && isNotBlank(replyToEmail);
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
