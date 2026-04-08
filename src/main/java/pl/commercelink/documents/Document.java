package pl.commercelink.documents;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBDocument;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIgnore;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConvertedEnum;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

@DynamoDBDocument
public class Document {

    @DynamoDBAttribute(attributeName = "id")
    private String id;
    @DynamoDBAttribute(attributeName = "number")
    private String number;
    @DynamoDBAttribute(attributeName = "link")
    private String link;
    @DynamoDBAttribute(attributeName = "type")
    @DynamoDBTypeConvertedEnum
    private DocumentType type;

    public Document() {
    }

    public Document(String id, String number, String link, DocumentType type) {
        this.id = id;
        this.number = number;
        this.link = link;
        this.type = type;
    }

    @DynamoDBIgnore
    public boolean hasId(String other) {
        return isNotBlank(id) && id.equalsIgnoreCase(other);
    }

    @DynamoDBIgnore
    public boolean hasNumberAndLink() {
        return isNotBlank(number) && isNotBlank(link);
    }

    @DynamoDBIgnore
    public boolean hasOneOfTypes(DocumentType... types) {
        for (DocumentType type : types) {
            if (this.type == type) {
                return true;
            }
        }
        return false;
    }

    @DynamoDBIgnore
    public boolean isExternal() {
        return isNotBlank(link);
    }

    @DynamoDBIgnore
    public String getViewUrl() {
        if (isNotBlank(link)) {
            return link;
        }

        if (type.isWarehouseDocument()) {
            return "/dashboard/warehouse-documents/details?documentNo=" + number;
        }

        return null;
    }

    // required by dynamodb
    public String getNumber() {
        return number;
    }

    public void setNumber(String number) {
        this.number = number;
    }

    public String getLink() {
        return link;
    }

    public void setLink(String link) {
        this.link = link;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public DocumentType getType() {
        return type;
    }

    public void setType(DocumentType type) {
        this.type = type;
    }
}
