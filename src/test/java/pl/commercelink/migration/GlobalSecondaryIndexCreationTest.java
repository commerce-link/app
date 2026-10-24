package pl.commercelink.migration;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The index creation of the migrations against a fake DescribeTable sequence: bounded, and never really sleeping. */
@ExtendWith(MockitoExtension.class)
class GlobalSecondaryIndexCreationTest {

    private static final Projection KEYS = new Projection().withProjectionType(ProjectionType.KEYS_ONLY);

    @Mock
    AmazonDynamoDB dynamoDB;

    final List<Long> pauses = new ArrayList<>();

    @Test
    void waitsWhileAnotherIndexIsBeingBuiltAndThenCreatesTheIndex() {
        // given
        when(dynamoDB.describeTable("Orders")).thenReturn(
                onDemand("ACTIVE", index("StoreIdStatusIndex", "CREATING")),
                onDemand("ACTIVE", index("StoreIdStatusIndex", "CREATING")),
                onDemand("UPDATING", index("StoreIdStatusIndex", "ACTIVE")),
                onDemand("ACTIVE", index("StoreIdStatusIndex", "ACTIVE")));

        // when
        boolean created = creation(Duration.ofMinutes(10)).createIfAbsent("Orders", "DiscountCodeIndex", "discountCode", "orderedAt", KEYS);

        // then
        assertThat(created).isTrue();
        assertThat(pauses).containsExactly(5000L, 5000L);
        UpdateTableRequest request = sentUpdate();
        assertThat(request.getTableName()).isEqualTo("Orders");
        assertThat(request.getAttributeDefinitions()).containsExactly(
                new AttributeDefinition("discountCode", ScalarAttributeType.S),
                new AttributeDefinition("orderedAt", ScalarAttributeType.S));
        CreateGlobalSecondaryIndexAction create = request.getGlobalSecondaryIndexUpdates().get(0).getCreate();
        assertThat(create.getIndexName()).isEqualTo("DiscountCodeIndex");
        assertThat(create.getKeySchema()).containsExactly(
                new KeySchemaElement("discountCode", KeyType.HASH), new KeySchemaElement("orderedAt", KeyType.RANGE));
        assertThat(create.getProjection()).isEqualTo(KEYS);
        assertThat(create.getProvisionedThroughput()).isNull();
    }

    @Test
    void anExistingIndexIsLeftAlone() {
        // given
        when(dynamoDB.describeTable("Orders")).thenReturn(onDemand("ACTIVE", index("DiscountCodeIndex", "CREATING")));

        // when
        boolean created = creation(Duration.ofMinutes(10)).createIfAbsent("Orders", "DiscountCodeIndex", "discountCode", "orderedAt", KEYS);

        // then
        assertThat(created).isFalse();
        assertThat(pauses).isEmpty();
        verify(dynamoDB, never()).updateTable(any(UpdateTableRequest.class));
    }

    @Test
    void failsNamingTheBusyIndexWhenItNeverBecomesActive() {
        // given
        when(dynamoDB.describeTable("Orders")).thenReturn(onDemand("UPDATING", index("StoreIdStatusIndex", "CREATING")));

        // when / then
        assertThatThrownBy(() -> creation(Duration.ofMinutes(1)).createIfAbsent("Orders", "DiscountCodeIndex", "discountCode", "orderedAt", KEYS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("V099")
                .hasMessageContaining("StoreIdStatusIndex (CREATING)")
                .hasMessageContaining("Orders");
        assertThat(pauses).hasSize(12);
        verify(dynamoDB, never()).updateTable(any(UpdateTableRequest.class));
    }

    @Test
    void aProvisionedTableGivesTheIndexItsOwnThroughput() {
        // given
        TableDescription provisioned = new TableDescription().withTableStatus("ACTIVE")
                .withProvisionedThroughput(new ProvisionedThroughputDescription().withReadCapacityUnits(7L).withWriteCapacityUnits(0L));
        when(dynamoDB.describeTable("Orders")).thenReturn(new DescribeTableResult().withTable(provisioned));

        // when
        creation(Duration.ofMinutes(10)).createIfAbsent("Orders", "DiscountCodeIndex", "discountCode", "orderedAt", KEYS);

        // then
        ProvisionedThroughput throughput = sentUpdate().getGlobalSecondaryIndexUpdates().get(0).getCreate().getProvisionedThroughput();
        assertThat(throughput.getReadCapacityUnits()).isEqualTo(7L);
        assertThat(throughput.getWriteCapacityUnits()).isEqualTo(1L);
    }

    private GlobalSecondaryIndexCreation creation(Duration timeout) {
        return new GlobalSecondaryIndexCreation(dynamoDB, "V099", pauses::add, timeout);
    }

    private UpdateTableRequest sentUpdate() {
        ArgumentCaptor<UpdateTableRequest> request = ArgumentCaptor.forClass(UpdateTableRequest.class);
        verify(dynamoDB).updateTable(request.capture());
        return request.getValue();
    }

    private static DescribeTableResult onDemand(String status, GlobalSecondaryIndexDescription... indexes) {
        return new DescribeTableResult().withTable(new TableDescription().withTableStatus(status)
                .withBillingModeSummary(new BillingModeSummary().withBillingMode(BillingMode.PAY_PER_REQUEST))
                .withGlobalSecondaryIndexes(indexes.length == 0 ? null : List.of(indexes)));
    }

    private static GlobalSecondaryIndexDescription index(String name, String status) {
        return new GlobalSecondaryIndexDescription().withIndexName(name).withIndexStatus(status);
    }
}
