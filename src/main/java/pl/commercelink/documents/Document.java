package pl.commercelink.documents;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBDocument;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIgnore;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConverted;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConvertedEnum;
import org.springframework.format.annotation.DateTimeFormat;
import pl.commercelink.starter.dynamodb.DynamoDbLocalDateConverter;

import java.time.LocalDate;

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
    @DynamoDBAttribute(attributeName = "issuedAt")
    @DynamoDBTypeConverted(converter = DynamoDbLocalDateConverter.class)
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate issuedAt;

    public Document() {
    }

    public Document(String id, String number, String link, DocumentType type) {
        this.id = id;
        this.number = number;
        this.link = link;
        this.type = type;
    }

    public Document(String id, String number, String link, DocumentType type, LocalDate issuedAt) {
        this.id = id;
        this.number = number;
        this.link = link;
        this.type = type;
        this.issuedAt = issuedAt;
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
            return "/dashboard/warehouse-documents/details?documentId=" + id;
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

    public LocalDate getIssuedAt() {
        return issuedAt;
    }

    public void setIssuedAt(LocalDate issuedAt) {
        this.issuedAt = issuedAt;
    }
}
