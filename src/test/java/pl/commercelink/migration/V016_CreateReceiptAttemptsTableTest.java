package pl.commercelink.migration;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.model.CreateTableRequest;
import com.amazonaws.services.dynamodbv2.model.DescribeTableRequest;
import com.amazonaws.services.dynamodbv2.model.DescribeTableResult;
import com.amazonaws.services.dynamodbv2.model.TableDescription;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import pl.commercelink.receipts.ReceiptAttempt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class V016_CreateReceiptAttemptsTableTest {

    @Test
    void createsTheTableWithTheDueIndex() {
        AmazonDynamoDB dynamoDB = mock(AmazonDynamoDB.class);
        // the starter's createTableIfAbsent (0.1.11+) waits for ACTIVE through this overload before returning
        when(dynamoDB.describeTable(any(DescribeTableRequest.class)))
                .thenReturn(new DescribeTableResult().withTable(new TableDescription()
                        .withTableName(ReceiptAttempt.TABLE_NAME).withTableStatus("ACTIVE")));

        new V016_CreateReceiptAttemptsTable(dynamoDB).createTable();

        ArgumentCaptor<CreateTableRequest> request = ArgumentCaptor.forClass(CreateTableRequest.class);
        verify(dynamoDB).createTable(request.capture());
        assertThat(request.getValue().getTableName()).isEqualTo(ReceiptAttempt.TABLE_NAME);
        assertThat(request.getValue().getKeySchema()).extracting(k -> k.getAttributeName())
                .containsExactly("storeId", "receiptKey");
        assertThat(request.getValue().getGlobalSecondaryIndexes()).singleElement().satisfies(index -> {
            assertThat(index.getIndexName()).isEqualTo(ReceiptAttempt.DUE_INDEX);
            assertThat(index.getKeySchema()).extracting(k -> k.getAttributeName())
                    .containsExactly("dueBucket", "nextCheckAt");
        });
    }
}
