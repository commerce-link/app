package pl.commercelink.discounts;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIgnore;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBRangeKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConverted;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConvertedEnum;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pl.commercelink.starter.dynamodb.DynamoDbLocalDateTimeConverter;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@DynamoDBTable(tableName = DiscountCode.TABLE_NAME)
@Getter
@Setter
@NoArgsConstructor
public class DiscountCode {

    public static final String TABLE_NAME = "DiscountCodes";
    public static final ZoneId ZONE = ZoneId.of("Europe/Warsaw");

    private static final Pattern WELL_FORMED = Pattern.compile("[A-Z0-9-]{3,32}");

    @DynamoDBHashKey(attributeName = "storeId")
    private String storeId;
    @DynamoDBRangeKey(attributeName = "code")
    private String code;
    @DynamoDBAttribute(attributeName = "name")
    private String name;
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
    @DynamoDBAttribute(attributeName = "paymentProvider")
    private String paymentProvider;
    @DynamoDBAttribute(attributeName = "minOrderValue")
    private double minOrderValue;
    @DynamoDBAttribute(attributeName = "maxUses")
    private int maxUses;
    @DynamoDBAttribute(attributeName = "usedCount")
    private int usedCount;
    @DynamoDBAttribute(attributeName = "validFrom")
    @DynamoDBTypeConverted(converter = DynamoDbLocalDateTimeConverter.class)
    private LocalDateTime validFrom;
    @DynamoDBAttribute(attributeName = "validTo")
    @DynamoDBTypeConverted(converter = DynamoDbLocalDateTimeConverter.class)
    private LocalDateTime validTo;
    @DynamoDBAttribute(attributeName = "active")
    private boolean active;

    public static String normalize(String raw) {
        return raw == null ? null : raw.trim().toUpperCase(Locale.ROOT);
    }

    public static boolean isWellFormed(String normalized) {
        return normalized != null && WELL_FORMED.matcher(normalized).matches();
    }

    @DynamoDBIgnore
    public boolean isExhausted() {
        return maxUses > 0 && usedCount >= maxUses;
    }

    public DiscountCodeStatus statusAt(LocalDateTime now) {
        if (!active) {
            return DiscountCodeStatus.Disabled;
        }
        if (isExhausted()) {
            return DiscountCodeStatus.Exhausted;
        }
        if (validTo != null && now.isAfter(validTo)) {
            return DiscountCodeStatus.Expired;
        }
        if (validFrom != null && now.isBefore(validFrom)) {
            return DiscountCodeStatus.Scheduled;
        }
        return DiscountCodeStatus.Active;
    }
}
