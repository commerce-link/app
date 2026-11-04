package pl.commercelink.migration;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;
import com.amazonaws.services.dynamodbv2.model.ScanRequest;
import com.amazonaws.services.dynamodbv2.model.ScanResult;
import com.amazonaws.services.dynamodbv2.model.UpdateItemRequest;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.RequiredArgsConstructor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Renames mfn to sku in the items of every basket and offer. A basket is rewritten only while it is still at the
 * version the scan saw, and the version is kept, so an offer page opened before keeps saving.
 */
@ChangeUnit(id = "V024-rename-basket-item-mfn-to-sku", order = "024", author = "commercelink")
@RequiredArgsConstructor
public class V024_RenameBasketItemMfnToSku {

    private static final String TABLE_NAME = "Baskets";
    private static final String ITEMS = "basketItems";
    private static final String OLD_ATTRIBUTE = "mfn";
    private static final String NEW_ATTRIBUTE = "sku";

    private final AmazonDynamoDB dynamoDB;

    @Execution
    public void execute() {
        Map<String, AttributeValue> startKey = null;
        do {
            ScanResult page = dynamoDB.scan(new ScanRequest()
                    .withTableName(TABLE_NAME)
                    .withProjectionExpression("storeId, basketId, " + ITEMS + ", #v")
                    .withExpressionAttributeNames(Map.of("#v", "version"))
                    .withExclusiveStartKey(startKey));
            page.getItems().stream().filter(V024_RenameBasketItemMfnToSku::hasOldAttribute).forEach(this::rename);
            startKey = page.getLastEvaluatedKey();
        } while (startKey != null && !startKey.isEmpty());
    }

    private static boolean hasOldAttribute(Map<String, AttributeValue> basket) {
        AttributeValue items = basket.get(ITEMS);
        return items != null && items.getL() != null
                && items.getL().stream().anyMatch(item -> item.getM() != null && item.getM().containsKey(OLD_ATTRIBUTE));
    }

    private void rename(Map<String, AttributeValue> basket) {
        List<AttributeValue> items = basket.get(ITEMS).getL().stream().map(V024_RenameBasketItemMfnToSku::renamed).toList();
        Map<String, AttributeValue> values = new HashMap<>();
        values.put(":items", new AttributeValue().withL(items));
        String condition = "attribute_exists(basketId) AND attribute_not_exists(#v)";
        AttributeValue version = basket.get("version");
        if (version != null) {
            condition = "attribute_exists(basketId) AND #v = :v";
            values.put(":v", version);
        }
        try {
            dynamoDB.updateItem(new UpdateItemRequest()
                    .withTableName(TABLE_NAME)
                    .withKey(Map.of("storeId", basket.get("storeId"), "basketId", basket.get("basketId")))
                    .withUpdateExpression("SET " + ITEMS + " = :items")
                    .withConditionExpression(condition)
                    .withExpressionAttributeNames(Map.of("#v", "version"))
                    .withExpressionAttributeValues(values));
        } catch (ConditionalCheckFailedException changedMeanwhile) {
            // deleted or saved since the scan; what an older instance saved with mfn BasketItem still reads
        }
    }

    private static AttributeValue renamed(AttributeValue item) {
        if (item.getM() == null || !item.getM().containsKey(OLD_ATTRIBUTE)) {
            return item;
        }
        Map<String, AttributeValue> fields = new HashMap<>(item.getM());
        AttributeValue mfn = fields.remove(OLD_ATTRIBUTE);
        fields.putIfAbsent(NEW_ATTRIBUTE, mfn);
        return new AttributeValue().withM(fields);
    }

    @RollbackExecution
    public void rollback() {
    }
}
