package pl.commercelink.templates;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBDocument;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIgnore;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

@DynamoDBDocument
public class EmailAttachment {
    @DynamoDBAttribute(attributeName = "name")
    private String name;
    @DynamoDBAttribute(attributeName = "url")
    private String url;

    public EmailAttachment() {
    }

    public EmailAttachment(String name, String url) {
        this.name = name;
        this.url = url;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    @DynamoDBIgnore
    public boolean isComplete() {
        return isNotBlank(name) && isNotBlank(url);
    }
}