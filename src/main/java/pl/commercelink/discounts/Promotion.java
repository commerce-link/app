package pl.commercelink.discounts;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBRangeKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConverted;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConvertedEnum;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pl.commercelink.starter.dynamodb.DynamoDbLocalDateTimeConverter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@DynamoDBTable(tableName = Promotion.TABLE_NAME)
@Getter
@Setter
@NoArgsConstructor
public class Promotion implements Discount {

    public static final String TABLE_NAME = "Promotions";

    @DynamoDBHashKey(attributeName = "storeId")
    private String storeId;
    @DynamoDBRangeKey(attributeName = "promotionId")
    private String promotionId;
    @DynamoDBAttribute(attributeName = "name")
    private String name;
    @DynamoDBAttribute(attributeName = "conditionTarget")
    @DynamoDBTypeConvertedEnum
    private DiscountTarget conditionTarget;
    @DynamoDBAttribute(attributeName = "conditionValues")
    private List<TargetValue> conditionValues = new ArrayList<>();
    @DynamoDBAttribute(attributeName = "conditionQuantity")
    private int conditionQuantity;
    @DynamoDBAttribute(attributeName = "minOrderValue")
    private double minOrderValue;
    @DynamoDBAttribute(attributeName = "type")
    @DynamoDBTypeConvertedEnum
    private DiscountType type;
    @DynamoDBAttribute(attributeName = "value")
    private double value;
    @DynamoDBAttribute(attributeName = "target")
    @DynamoDBTypeConvertedEnum
    private DiscountTarget target;
    @DynamoDBAttribute(attributeName = "targetValues")
    private List<TargetValue> targetValues = new ArrayList<>();
    @DynamoDBAttribute(attributeName = "validFrom")
    @DynamoDBTypeConverted(converter = DynamoDbLocalDateTimeConverter.class)
    private LocalDateTime validFrom;
    @DynamoDBAttribute(attributeName = "validTo")
    @DynamoDBTypeConverted(converter = DynamoDbLocalDateTimeConverter.class)
    private LocalDateTime validTo;
    @DynamoDBAttribute(attributeName = "active")
    private boolean active;
    @DynamoDBAttribute(attributeName = "createdAt")
    @DynamoDBTypeConverted(converter = DynamoDbLocalDateTimeConverter.class)
    private LocalDateTime createdAt;
}
