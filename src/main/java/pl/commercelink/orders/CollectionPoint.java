package pl.commercelink.orders;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBDocument;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIgnore;
import org.apache.commons.lang3.StringUtils;

@DynamoDBDocument
public class CollectionPoint {

    @DynamoDBAttribute(attributeName = "code")
    private String code;
    @DynamoDBAttribute(attributeName = "operator")
    private String operator;
    @DynamoDBAttribute(attributeName = "name")
    private String name;

    public CollectionPoint() {
    }

    public CollectionPoint(String code, String operator, String name) {
        this.code = code;
        this.operator = operator;
        this.name = name;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @DynamoDBIgnore
    public boolean isDispatchable() {
        return StringUtils.isNotBlank(code) && StringUtils.isNotBlank(operator);
    }

    @DynamoDBIgnore
    public String getDisplayLabel() {
        if (StringUtils.isBlank(name)) {
            return code;
        }
        return code + " (" + name + ")";
    }

    @DynamoDBIgnore
    public CollectionPoint copy() {
        return new CollectionPoint(code, operator, name);
    }
}
