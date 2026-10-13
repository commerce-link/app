package pl.commercelink.orders.rma;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RMARepositoryListIndexStatusTest {

    @Mock
    AmazonDynamoDB dynamoDB;

    private static DescribeTableResult table(String indexStatus) {
        TableDescription table = new TableDescription();
        if (indexStatus != null) {
            table.withGlobalSecondaryIndexes(new GlobalSecondaryIndexDescription()
                    .withIndexName(RMARepository.LIST_INDEX).withIndexStatus(indexStatus));
        }
        return new DescribeTableResult().withTable(table);
    }

    @Test
    void aBuildingIndexIsCheckedAgainOnlyAfterAMinuteAndAnActiveOneNeverAgain() {
        // given
        AtomicLong now = new AtomicLong(1_000_000);
        RMARepository rmas = new RMARepository(dynamoDB, now::get);
        when(dynamoDB.describeTable("RMA")).thenReturn(table("CREATING"), table("ACTIVE"));

        // when
        boolean first = rmas.isListIndexActive();
        now.addAndGet(30_000);
        boolean withinAMinute = rmas.isListIndexActive();
        now.addAndGet(31_000);
        boolean afterAMinute = rmas.isListIndexActive();
        now.addAndGet(600_000);
        boolean later = rmas.isListIndexActive();

        // then
        assertThat(first).isFalse();
        assertThat(withinAMinute).isFalse();
        assertThat(afterAMinute).isTrue();
        assertThat(later).isTrue();
        verify(dynamoDB, times(2)).describeTable("RMA");
    }

    @Test
    void anUnreadableStatusMeansThePartition() {
        // given
        RMARepository rmas = new RMARepository(dynamoDB, () -> 0L);
        when(dynamoDB.describeTable("RMA")).thenThrow(new AmazonDynamoDBException("AccessDenied"));

        // when / then
        assertThat(rmas.isListIndexActive()).isFalse();
    }
}
