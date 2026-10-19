package pl.commercelink.migration;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** The waits of V020 against a fake DescribeTable sequence: bounded, and never really sleeping. */
@ExtendWith(MockitoExtension.class)
class V020WaitingTest {

    @Mock
    AmazonDynamoDB dynamoDB;

    final List<Long> pauses = new ArrayList<>();

    @Test
    void waitsWhileAnIndexOfV019IsStillBeingCreatedAndThenGoesOn() {
        // given
        when(dynamoDB.describeTable("Deliveries")).thenReturn(
                table("ACTIVE", index("StoreIdListKeyIndex", "CREATING")),
                table("ACTIVE", index("StoreIdListKeyIndex", "CREATING")),
                table("ACTIVE", index("StoreIdListKeyIndex", "ACTIVE")),
                table("ACTIVE", index("StoreIdListKeyIndex", "ACTIVE")),
                table("ACTIVE", index("StoreIdListKeyIndex", "DELETING")),
                table("ACTIVE"));
        when(dynamoDB.scan(any(ScanRequest.class))).thenReturn(new ScanResult().withItems(List.of()));

        // when
        migration(Duration.ofMinutes(10)).execute();

        // then
        assertThat(pauses).containsExactly(5000L, 5000L, 5000L);
        verify(dynamoDB).updateTable(argThat((UpdateTableRequest r) -> r.getGlobalSecondaryIndexUpdates().get(0).getDelete() != null));
        verify(dynamoDB).updateTable(argThat((UpdateTableRequest r) -> r.getGlobalSecondaryIndexUpdates().get(0).getCreate() != null));
    }

    @Test
    void failsNamingTheIndexWhenTheOneInProgressNeverBecomesActive() {
        // given
        when(dynamoDB.describeTable("Deliveries")).thenReturn(table("UPDATING", index("StoreIdListKeyIndex", "CREATING")));

        // when / then
        assertThatThrownBy(() -> migration(Duration.ofMinutes(10)).execute())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("StoreIdListKeyIndex");
        assertThat(pauses).hasSize(120);
        verify(dynamoDB, never()).updateTable(any(UpdateTableRequest.class));
        verify(dynamoDB, never()).scan(any(ScanRequest.class));
    }

    @Test
    void failsNamingTheOldIndexWhenItIsNeverGone() {
        // given: idle once (so the delete is issued), then the index stays in DELETING
        when(dynamoDB.describeTable("Deliveries")).thenReturn(
                table("ACTIVE", index("StoreIdListKeyIndex", "ACTIVE")),
                table("ACTIVE", index("StoreIdListKeyIndex", "DELETING")));

        // when / then
        assertThatThrownBy(() -> migration(Duration.ofMinutes(1)).execute())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("StoreIdListKeyIndex");
        assertThat(pauses).hasSize(12);
        verify(dynamoDB, never()).scan(any(ScanRequest.class));
    }

    private V020_RenameDeliveriesListSortKey migration(Duration timeout) {
        return new V020_RenameDeliveriesListSortKey(dynamoDB, pauses::add, timeout);
    }

    private static DescribeTableResult table(String status, GlobalSecondaryIndexDescription... indexes) {
        return new DescribeTableResult().withTable(new TableDescription().withTableStatus(status)
                .withBillingModeSummary(new BillingModeSummary().withBillingMode(BillingMode.PAY_PER_REQUEST))
                .withGlobalSecondaryIndexes(indexes.length == 0 ? null : List.of(indexes)));
    }

    private static GlobalSecondaryIndexDescription index(String name, String status) {
        return new GlobalSecondaryIndexDescription().withIndexName(name).withIndexStatus(status);
    }
}
