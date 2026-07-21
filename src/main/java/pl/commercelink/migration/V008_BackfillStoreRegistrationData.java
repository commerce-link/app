package pl.commercelink.migration;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static pl.commercelink.starter.migration.DynamoDbMigrationSupport.executeUpdate;
import static pl.commercelink.starter.migration.DynamoDbMigrationSupport.scanAndProcess;

@ChangeUnit(id = "V008-backfill-store-registration-data", order = "008", author = "commercelink")
@RequiredArgsConstructor
public class V008_BackfillStoreRegistrationData {

    private static final String TABLE_NAME = "Stores";

    private final AmazonDynamoDB dynamoDB;

    @Execution
    public void backfillRegistrationData() {
        scanAndProcess(dynamoDB, TABLE_NAME, List.of("storeId", "demo", "billingDetails", "createdAt"), item -> {
            BackfillUpdate update = buildUpdate(item);
            if (update != null) {
                executeUpdate(dynamoDB, TABLE_NAME, Map.of("storeId", item.get("storeId")),
                        update.expression(), null, update.values());
            }
        });
    }

    static BackfillUpdate buildUpdate(Map<String, AttributeValue> item) {
        AttributeValue demo = item.get("demo");
        if (demo == null || demo.getM() == null) {
            return null;
        }
        List<String> clauses = new ArrayList<>();
        Map<String, AttributeValue> values = new HashMap<>();

        String ownerEmail = stringValue(demo.getM().get("ownerEmail"));
        AttributeValue billingDetails = item.get("billingDetails");
        if (ownerEmail != null) {
            if (billingDetails == null || billingDetails.getM() == null) {
                clauses.add("billingDetails = if_not_exists(billingDetails, :billing)");
                values.put(":billing", new AttributeValue().withM(
                        Map.of("email", new AttributeValue().withS(ownerEmail))));
            } else if (stringValue(billingDetails.getM().get("email")) == null) {
                clauses.add("billingDetails.email = if_not_exists(billingDetails.email, :email)");
                values.put(":email", new AttributeValue().withS(ownerEmail));
            }
        }

        String demoCreatedAt = stringValue(demo.getM().get("createdAt"));
        if (demoCreatedAt != null && stringValue(item.get("createdAt")) == null) {
            clauses.add("createdAt = if_not_exists(createdAt, :createdAt)");
            values.put(":createdAt", new AttributeValue().withS(demoCreatedAt));
        }

        if (clauses.isEmpty()) {
            return null;
        }
        return new BackfillUpdate("SET " + String.join(", ", clauses), values);
    }

    private static String stringValue(AttributeValue attribute) {
        if (attribute == null || attribute.getS() == null || attribute.getS().isBlank()) {
            return null;
        }
        return attribute.getS();
    }

    record BackfillUpdate(String expression, Map<String, AttributeValue> values) {
    }

    @RollbackExecution
    public void rollback() {
    }
}
