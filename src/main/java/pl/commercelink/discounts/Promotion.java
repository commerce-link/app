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
import pl.commercelink.baskets.Basket;
import pl.commercelink.baskets.BasketItem;
import pl.commercelink.starter.dynamodb.DynamoDbLocalDateTimeConverter;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.function.Function;

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
    @DynamoDBAttribute(attributeName = "conditionScope")
    private DiscountScope conditionScope = new DiscountScope();
    @DynamoDBAttribute(attributeName = "conditionQuantity")
    private int conditionQuantity;
    @DynamoDBAttribute(attributeName = "minOrderValue")
    private double minOrderValue;
    @DynamoDBAttribute(attributeName = "type")
    @DynamoDBTypeConvertedEnum
    private DiscountType type;
    @DynamoDBAttribute(attributeName = "value")
    private double value;
    @DynamoDBAttribute(attributeName = "scope")
    private DiscountScope scope = new DiscountScope();
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

    boolean isMetBy(Basket basket, Function<String, Set<String>> productAliases) {
        return isMinimumReachedBy(basket) && isConditionMetBy(basket, productAliases);
    }

    private boolean isConditionMetBy(Basket basket, Function<String, Set<String>> productAliases) {
        if (conditionScope.isEmpty()) {
            return true;
        }
        long quantity = basket.getEffectiveBasketItems().stream()
                .filter(item -> conditionScope.matches(item, productAliases))
                .mapToLong(BasketItem::getQty)
                .sum();
        return quantity >= conditionQuantity;
    }
}
