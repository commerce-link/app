package pl.commercelink.receipts;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.model.GetItemRequest;
import com.amazonaws.services.dynamodbv2.model.GetItemResult;
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
}
