package pl.commercelink.receipts;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.GetItemRequest;
import com.amazonaws.services.dynamodbv2.model.GetItemResult;
import com.amazonaws.services.dynamodbv2.model.QueryRequest;
import com.amazonaws.services.dynamodbv2.model.QueryResult;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DynamoDbReceiptAttemptStoreTest {

    private final AmazonDynamoDB dynamoDB = mock(AmazonDynamoDB.class);
    private final DynamoDbReceiptAttemptStore store = new DynamoDbReceiptAttemptStore(dynamoDB);

    /** A stale replica read must never drive an update decision or a lease check. */
    @Test
    void findReadsStronglyConsistent() {
        when(dynamoDB.getItem(any())).thenReturn(new GetItemResult());

        store.find("s1", "o1:R1");

        ArgumentCaptor<GetItemRequest> request = ArgumentCaptor.forClass(GetItemRequest.class);
        verify(dynamoDB).getItem(request.capture());
        assertThat(request.getValue().isConsistentRead()).isTrue();
        assertThat(request.getValue().getTableName()).isEqualTo(ReceiptAttempt.TABLE_NAME);
        assertThat(request.getValue().getKey()).containsKeys("storeId", "receiptKey");
    }

    /** A FISCALISED attempt still needing its link (no document, no give-up) counts as live: the customer still
     *  waits for the e-mail, and switching the provider away would strand the poll. */
    @Test
    void hasLiveAttemptsFiltersForStatesStillNeedingTheProvider() {
        when(dynamoDB.query(any())).thenReturn(new QueryResult());

        store.hasLiveAttempts("s1");

        ArgumentCaptor<QueryRequest> request = ArgumentCaptor.forClass(QueryRequest.class);
        verify(dynamoDB).query(request.capture());
        assertThat(request.getValue().getFilterExpression()).contains("attribute_not_exists(documentUrl)");
        assertThat(request.getValue().getExpressionAttributeValues().values())
                .extracting(AttributeValue::getS)
                .contains(ReceiptAttemptState.FISCALISED.name());
    }
}
