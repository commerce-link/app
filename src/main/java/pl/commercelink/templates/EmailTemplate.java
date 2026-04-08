package pl.commercelink.templates;

import com.amazonaws.services.dynamodbv2.datamodeling.*;
import pl.commercelink.orders.notifications.EmailNotificationType;

import java.util.LinkedList;
import java.util.List;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

@DynamoDBTable(tableName = "EmailTemplates")
public class EmailTemplate {

    @DynamoDBHashKey(attributeName = "storeId")
    private String storeId;
    @DynamoDBRangeKey(attributeName = "templateName")
    private String templateName;
    @DynamoDBAttribute(attributeName = "type")
    @DynamoDBTypeConvertedEnum
    private EmailNotificationType type;
    @DynamoDBAttribute(attributeName = "subject")
    private String subject;
    @DynamoDBAttribute(attributeName = "textBody")
    private String textBody;
    @DynamoDBAttribute(attributeName = "attachments")
    private List<EmailAttachment> attachments = new LinkedList<>();
    @DynamoDBAttribute(attributeName = "bccAddresses")
    private List<String> bccAddresses = new LinkedList<>();

    public EmailTemplate() {
    }

    @DynamoDBIgnore
    public boolean isComplete() {
        return isNotBlank(storeId) && isNotBlank(templateName) && isNotBlank(subject) && isNotBlank(textBody);
    }

    public String getStoreId() {
        return storeId;
    }

    public void setStoreId(String storeId) {
        this.storeId = storeId;
    }

    public String getTemplateName() {
        return templateName;
    }

    public void setTemplateName(String templateName) {
        this.templateName = templateName;
    }

    public EmailNotificationType getType() {
        return type;
    }

    public void setType(EmailNotificationType type) {
        this.type = type;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getTextBody() {
        return textBody;
    }

    public void setTextBody(String textBody) {
        this.textBody = textBody;
    }

    public List<EmailAttachment> getAttachments() {
        return attachments;
    }

    public void setAttachments(List<EmailAttachment> attachments) {
        this.attachments = attachments;
    }

    public List<String> getBccAddresses() {
        return bccAddresses;
    }

    public void setBccAddresses(List<String> bccAddresses) {
        this.bccAddresses = bccAddresses;
    }
}
