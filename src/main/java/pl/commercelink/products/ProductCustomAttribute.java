package pl.commercelink.products;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBDocument;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIgnore;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

@DynamoDBDocument
public class ProductCustomAttribute {

    @JsonProperty("name")
    @DynamoDBAttribute(attributeName = "name")
    private String name;
    @JsonProperty("value")
    @DynamoDBAttribute(attributeName = "value")
    private String value;

    public ProductCustomAttribute() {
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    @JsonIgnore
    @DynamoDBIgnore
    public boolean isComplete() {
        return isNotBlank(name) && isNotBlank(value);
    }

    @JsonIgnore
    @DynamoDBIgnore
    @Override
    public String toString() {
        return name + " : " + value;
    }
}
