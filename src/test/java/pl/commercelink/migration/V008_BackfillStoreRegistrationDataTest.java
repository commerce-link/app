package pl.commercelink.migration;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ScanRequest;
import com.amazonaws.services.dynamodbv2.model.ScanResult;
import com.amazonaws.services.dynamodbv2.model.UpdateItemRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class V008_BackfillStoreRegistrationDataTest {

    @Mock
    private AmazonDynamoDB dynamoDB;
    @InjectMocks
    private V008_BackfillStoreRegistrationData migration;

    private static AttributeValue string(String value) {
        return new AttributeValue().withS(value);
    }

    private static AttributeValue demo(String ownerEmail, String createdAt) {
        Map<String, AttributeValue> demo = new HashMap<>();
        if (ownerEmail != null) {
            demo.put("ownerEmail", string(ownerEmail));
        }
        if (createdAt != null) {
            demo.put("createdAt", string(createdAt));
        }
        return new AttributeValue().withM(demo);
    }

    @Test
    void backfillsEmailAndCreatedAtWhenBothMissing() {
        // given
        Map<String, AttributeValue> item = Map.of(
                "storeId", string("store-1"),
                "demo", demo("a@b.pl", "2026-07-01T10:00:00Z"));

        // when
        V008_BackfillStoreRegistrationData.BackfillUpdate update =
                V008_BackfillStoreRegistrationData.buildUpdate(item);

        // then
        assertEquals("SET billingDetails = if_not_exists(billingDetails, :billing), " +
                "createdAt = if_not_exists(createdAt, :createdAt)", update.expression());
        assertEquals("a@b.pl", update.values().get(":billing").getM().get("email").getS());
        assertEquals("2026-07-01T10:00:00Z", update.values().get(":createdAt").getS());
    }

    @Test
    void setsNestedEmailWhenBillingDetailsExistsWithoutEmail() {
        // given
        Map<String, AttributeValue> item = Map.of(
                "storeId", string("store-1"),
                "demo", demo("a@b.pl", null),
                "createdAt", string("2026-07-01T10:00:00Z"),
                "billingDetails", new AttributeValue().withM(Map.of("city", string("Kraków"))));

        // when
        V008_BackfillStoreRegistrationData.BackfillUpdate update =
                V008_BackfillStoreRegistrationData.buildUpdate(item);

        // then
        assertEquals("SET billingDetails.email = if_not_exists(billingDetails.email, :email)", update.expression());
        assertEquals("a@b.pl", update.values().get(":email").getS());
    }

    @Test
    void backfillsOnlyCreatedAtWhenBillingComplete() {
        // given
        Map<String, AttributeValue> item = Map.of(
                "storeId", string("store-1"),
                "demo", demo("a@b.pl", "2026-07-01T10:00:00Z"),
                "billingDetails", new AttributeValue().withM(Map.of("email", string("x@y.pl"))));

        // when
        V008_BackfillStoreRegistrationData.BackfillUpdate update =
                V008_BackfillStoreRegistrationData.buildUpdate(item);

        // then
        assertEquals("SET createdAt = if_not_exists(createdAt, :createdAt)", update.expression());
        assertEquals("2026-07-01T10:00:00Z", update.values().get(":createdAt").getS());
    }

    @Test
    void skipsStoreWithCompleteData() {
        // given
        Map<String, AttributeValue> item = Map.of(
                "storeId", string("store-1"),
                "demo", demo("a@b.pl", "2026-07-01T10:00:00Z"),
                "createdAt", string("2026-07-01T10:00:00Z"),
                "billingDetails", new AttributeValue().withM(Map.of("email", string("a@b.pl"))));

        // when / then
        assertNull(V008_BackfillStoreRegistrationData.buildUpdate(item));
    }

    @Test
    void skipsNonDemoStore() {
        // given
        Map<String, AttributeValue> item = Map.of("storeId", string("store-1"));

        // when / then
        assertNull(V008_BackfillStoreRegistrationData.buildUpdate(item));
    }

    @Test
    void skipsEmailClauseForBlankOwnerEmail() {
        // given
        Map<String, AttributeValue> item = Map.of(
                "storeId", string("store-1"),
                "demo", demo(" ", "2026-07-01T10:00:00Z"));

        // when
        V008_BackfillStoreRegistrationData.BackfillUpdate update =
                V008_BackfillStoreRegistrationData.buildUpdate(item);

        // then
        assertEquals("SET createdAt = if_not_exists(createdAt, :createdAt)", update.expression());
        assertFalse(update.values().containsKey(":billing"));
    }

    @Test
    void executesUpdateOnlyForStoresNeedingBackfill() {
        // given
        Map<String, AttributeValue> needsBackfill = Map.of(
                "storeId", string("store-1"),
                "demo", demo("a@b.pl", "2026-07-01T10:00:00Z"));
        Map<String, AttributeValue> complete = Map.of(
                "storeId", string("store-2"),
                "demo", demo("c@d.pl", "2026-07-02T10:00:00Z"),
                "createdAt", string("2026-07-02T10:00:00Z"),
                "billingDetails", new AttributeValue().withM(Map.of("email", string("c@d.pl"))));
        when(dynamoDB.scan(any(ScanRequest.class)))
                .thenReturn(new ScanResult().withItems(List.of(needsBackfill, complete)));

        // when
        migration.backfillRegistrationData();

        // then
        ArgumentCaptor<UpdateItemRequest> captor = ArgumentCaptor.forClass(UpdateItemRequest.class);
        verify(dynamoDB).updateItem(captor.capture());
        assertEquals("Stores", captor.getValue().getTableName());
        assertEquals("store-1", captor.getValue().getKey().get("storeId").getS());
        assertTrue(captor.getValue().getUpdateExpression().startsWith("SET billingDetails"));
    }
}
