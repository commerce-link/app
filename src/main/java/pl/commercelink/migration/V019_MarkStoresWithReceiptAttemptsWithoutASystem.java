package pl.commercelink.migration;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.QueryRequest;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.RequiredArgsConstructor;
import pl.commercelink.receipts.ReceiptAttempt;
import pl.commercelink.stores.IntegrationType;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import static pl.commercelink.starter.migration.DynamoDbMigrationSupport.executeUpdate;
import static pl.commercelink.starter.migration.DynamoDbMigrationSupport.scanAndProcess;

/**
 * The order lifecycle reconciles the bell alerts of e-receipt attempts only for stores that may have attempts
 * (ReceiptTrigger#mayHaveAttempts: a receipt system chosen, automatic receipts ever switched on, or
 * {@code receipts.disconnectedAt}). A store that issued e-receipts only by hand and disconnected its system before
 * {@code disconnectedAt} existed has none of the three, yet its orders keep their attempts and alerts: this marks such
 * a store with the moment of the backfill (the real one is unknown). Only stores that really have an attempt are
 * marked; the others stay out of the lifecycle's attempts query.
 */
@ChangeUnit(id = "V019-mark-stores-with-receipt-attempts-without-a-system", order = "019", author = "commercelink")
@RequiredArgsConstructor
public class V019_MarkStoresWithReceiptAttemptsWithoutASystem {

    private static final String STORES = "Stores";

    private final AmazonDynamoDB dynamoDB;

    @Execution
    public void markStores() {
        String now = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        scanAndProcess(dynamoDB, STORES, List.of("storeId", "integrations", "receipts"), store -> {
            if (needsMarking(store) && hasAttempts(store.get("storeId").getS())) {
                mark(store, now);
            }
        });
    }

    /** No receipt system chosen, automatic receipts never switched on and no disconnection recorded. */
    static boolean needsMarking(Map<String, AttributeValue> store) {
        AttributeValue integrations = store.get("integrations");
        boolean hasSystem = integrations != null && integrations.getL() != null && integrations.getL().stream()
                .anyMatch(i -> i.getM() != null && i.getM().get("type") != null
                        && IntegrationType.RECEIPT_PROVIDER.name().equals(i.getM().get("type").getS()));
        Map<String, AttributeValue> receipts = store.get("receipts") == null ? null : store.get("receipts").getM();
        boolean marked = receipts != null && (receipts.get("enabledAt") != null || receipts.get("disconnectedAt") != null);
        return !hasSystem && !marked;
    }

    private boolean hasAttempts(String storeId) {
        return dynamoDB.query(new QueryRequest()
                .withTableName(ReceiptAttempt.TABLE_NAME)
                .withKeyConditionExpression("storeId = :s")
                .withExpressionAttributeValues(Map.of(":s", new AttributeValue().withS(storeId)))
                .withProjectionExpression("storeId")
                .withLimit(1)).getCount() > 0;
    }

    private void mark(Map<String, AttributeValue> store, String now) {
        Map<String, AttributeValue> key = Map.of("storeId", store.get("storeId"));
        AttributeValue moment = new AttributeValue().withS(now);
        if (store.get("receipts") == null || store.get("receipts").getM() == null) {
            executeUpdate(dynamoDB, STORES, key, "SET receipts = if_not_exists(receipts, :r)", null,
                    Map.of(":r", new AttributeValue().withM(Map.of("disconnectedAt", moment))));
        } else {
            executeUpdate(dynamoDB, STORES, key,
                    "SET receipts.disconnectedAt = if_not_exists(receipts.disconnectedAt, :d)", null,
                    Map.of(":d", moment));
        }
    }

    @RollbackExecution
    public void rollback() {
    }
}
