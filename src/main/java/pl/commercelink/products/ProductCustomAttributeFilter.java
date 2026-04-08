package pl.commercelink.products;

import pl.commercelink.taxonomy.ProductCategory;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBDocument;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIgnore;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConvertedEnum;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

@DynamoDBDocument
public class ProductCustomAttributeFilter {

    public enum Operator {
        GREATER_EQUAL_THAN(">="),
        LESS_EQUAL_THAN("<="),
        EQUALS("=");

        private final String operator;

        Operator(String operator) {
            this.operator = operator;
        }

        public String getOperator() {
            return operator;
        }

        private static Operator from(String text) {
            if (text.contains(">=")) {
                return Operator.GREATER_EQUAL_THAN;
            } else if (text.contains("<=")) {
                return Operator.LESS_EQUAL_THAN;
            } else if (text.contains("=")) {
                return Operator.EQUALS;
            }
            throw new RuntimeException("Unknown operator: " + text);
        }

        private static boolean isKnown(String op) {
            for (Operator o : Operator.values()) {
                if (o.operator.equals(op)) {
                    return true;
                }
            }
            return false;
        }
    }

    @DynamoDBAttribute(attributeName = "category")
    @DynamoDBTypeConvertedEnum
    private ProductCategory category;
    @JsonProperty("name")
    @DynamoDBAttribute(attributeName = "name")
    private String name;
    @JsonProperty("value")
    @DynamoDBAttribute(attributeName = "value")
    private String value;
    @JsonProperty("operator")
    @DynamoDBAttribute(attributeName = "operator")
    private String operator;

    public ProductCustomAttributeFilter() {
    }

    public ProductCategory getCategory() {
        return category;
    }

    public void setCategory(ProductCategory category) {
        this.category = category;
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

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }

    @JsonIgnore
    @DynamoDBIgnore
    public boolean isComplete() {
        return category != null && isNotBlank(name) && isNotBlank(value) && Operator.isKnown(operator);
    }

    @JsonIgnore
    @DynamoDBIgnore
    @Override
    public String toString() {
        return category + ":" + name + operator + value;
    }
}
