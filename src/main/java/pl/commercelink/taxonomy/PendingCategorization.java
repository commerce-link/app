package pl.commercelink.taxonomy;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConverted;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pl.commercelink.starter.dynamodb.DynamoDbLocalDateTimeConverter;

import java.time.LocalDateTime;

@DynamoDBTable(tableName = PendingCategorization.TABLE_NAME)
@Getter
@Setter
@NoArgsConstructor
public class PendingCategorization {

    public static final String TABLE_NAME = "TaxonomyCategoryPending";
    public static final String MFN = "mfn";
    public static final String SUPPLIER = "supplier";
    public static final String ATTEMPTS = "attempts";
    public static final String ADDED_AT = "addedAt";

    @DynamoDBHashKey(attributeName = MFN)
    private String mfn;

    @DynamoDBAttribute(attributeName = SUPPLIER)
    private String supplier;

    @DynamoDBAttribute(attributeName = ATTEMPTS)
    private Integer attempts;

    @DynamoDBAttribute(attributeName = ADDED_AT)
    @DynamoDBTypeConverted(converter = DynamoDbLocalDateTimeConverter.class)
    private LocalDateTime addedAt;

    public int attemptCount() {
        return attempts == null ? 0 : attempts;
    }
}
