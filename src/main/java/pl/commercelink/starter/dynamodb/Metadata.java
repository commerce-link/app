package pl.commercelink.starter.dynamodb;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBDocument;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIgnore;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

@DynamoDBDocument
public class Metadata {

    @JsonProperty("key")
    @DynamoDBAttribute(attributeName = "key")
    private String key;
    @JsonProperty("value")
    @DynamoDBAttribute(attributeName = "value")
    private String value;

    public Metadata() {
    }

    public Metadata(String key, String value) {
        this.key = key;
        this.value = value;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    @DynamoDBIgnore
    @JsonIgnore
    public boolean isComplete() {
        return isNotBlank(key) && isNotBlank(value);
    }

    @DynamoDBIgnore
    @JsonIgnore
    public boolean hasKey(String key) {
        return this.key.equalsIgnoreCase(key);
    }

    @DynamoDBIgnore
    @JsonIgnore
    @Override
    public String toString() {
        return key + " : " + value;
    }
}
