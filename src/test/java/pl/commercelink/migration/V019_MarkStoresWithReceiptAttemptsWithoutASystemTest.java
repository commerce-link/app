package pl.commercelink.migration;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.QueryRequest;
import com.amazonaws.services.dynamodbv2.model.QueryResult;
import com.amazonaws.services.dynamodbv2.model.ScanRequest;
import com.amazonaws.services.dynamodbv2.model.ScanResult;
import com.amazonaws.services.dynamodbv2.model.UpdateItemRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class V019_MarkStoresWithReceiptAttemptsWithoutASystemTest {

    @Mock
    private AmazonDynamoDB dynamoDB;
    @InjectMocks
    private V019_MarkStoresWithReceiptAttemptsWithoutASystem migration;

    private static AttributeValue string(String value) {
        return new AttributeValue().withS(value);
    }

    private static AttributeValue receiptSystem() {
        return new AttributeValue().withL(new AttributeValue().withM(Map.of(
                "type", string("RECEIPT_PROVIDER"), "name", string("fakturownia"))));
    }

    private static AttributeValue receipts(String field, String value) {
        return new AttributeValue().withM(Map.of(field, string(value)));
    }

    private void attempts(String storeId, int count) {
        when(dynamoDB.query(argThat((QueryRequest q) -> q != null
                && storeId.equals(q.getExpressionAttributeValues().get(":s").getS()))))
                .thenReturn(new QueryResult().withCount(count));
    }

    @Test
    void onlyAStoreWithoutAnyReceiptMarkNeedsMarking() {
        // when / then
        assertThat(V019_MarkStoresWithReceiptAttemptsWithoutASystem.needsMarking(Map.of("storeId", string("s")))).isTrue();
        assertThat(V019_MarkStoresWithReceiptAttemptsWithoutASystem.needsMarking(Map.of("storeId", string("s"),
                "integrations", receiptSystem()))).isFalse();
        assertThat(V019_MarkStoresWithReceiptAttemptsWithoutASystem.needsMarking(Map.of("storeId", string("s"),
                "receipts", receipts("enabledAt", "2026-09-28T10:00:00")))).isFalse();
        assertThat(V019_MarkStoresWithReceiptAttemptsWithoutASystem.needsMarking(Map.of("storeId", string("s"),
                "receipts", receipts("disconnectedAt", "2026-09-29T10:00:00")))).isFalse();
    }

    @Test
    void marksOnlyAStoreThatHasAttemptsButNoReceiptMark() {
        // given: s1 used its system by hand and disconnected it; s2 never had e-receipts; s3 has a system
        Map<String, AttributeValue> manualDisconnected = Map.of("storeId", string("s1"),
                "receipts", new AttributeValue().withM(Map.of("enabled", new AttributeValue().withN("0"))));
        Map<String, AttributeValue> never = Map.of("storeId", string("s2"));
        Map<String, AttributeValue> connected = Map.of("storeId", string("s3"), "integrations", receiptSystem());
        when(dynamoDB.scan(any(ScanRequest.class)))
                .thenReturn(new ScanResult().withItems(List.of(manualDisconnected, never, connected)));
        attempts("s1", 1);
        attempts("s2", 0);

        // when
        migration.markStores();

        // then
        ArgumentCaptor<UpdateItemRequest> captor = ArgumentCaptor.forClass(UpdateItemRequest.class);
        verify(dynamoDB).updateItem(captor.capture());
        assertThat(captor.getValue().getKey().get("storeId").getS()).isEqualTo("s1");
        assertThat(captor.getValue().getUpdateExpression())
                .isEqualTo("SET receipts.disconnectedAt = if_not_exists(receipts.disconnectedAt, :d)");
        verify(dynamoDB, never()).query(argThat((QueryRequest q) -> q != null
                && "s3".equals(q.getExpressionAttributeValues().get(":s").getS())));
    }

    @Test
    void aStoreWithoutReceiptSettingsGetsThemWithTheMark() {
        // given
        when(dynamoDB.scan(any(ScanRequest.class)))
                .thenReturn(new ScanResult().withItems(List.of(Map.of("storeId", string("s1")))));
        attempts("s1", 1);

        // when
        migration.markStores();

        // then
        ArgumentCaptor<UpdateItemRequest> captor = ArgumentCaptor.forClass(UpdateItemRequest.class);
        verify(dynamoDB).updateItem(captor.capture());
        assertThat(captor.getValue().getUpdateExpression()).isEqualTo("SET receipts = if_not_exists(receipts, :r)");
        assertThat(captor.getValue().getExpressionAttributeValues().get(":r").getM()).containsKey("disconnectedAt");
    }
}
