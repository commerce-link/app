package pl.commercelink.orders.rma;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class RmaListSortKeyTest {

    private static RMA rma(RMAStatus status, LocalDateTime createdAt) {
        RMA rma = new RMA();
        rma.setStoreId("store-1");
        rma.setRmaId("e3b0c442-98fc-4c14-9afb-f4c8996fb924");
        rma.setStatus(status);
        rma.setCreatedAt(createdAt);
        return rma;
    }

    @ParameterizedTest
    @EnumSource(value = RMAStatus.class, names = {"New", "Approved", "WaitingForItems", "ItemsReceived", "Processing"})
    void openStatusesAreActive(RMAStatus status) {
        // when
        String key = RmaListSortKey.of(rma(status, LocalDateTime.of(2026, 10, 13, 9, 5, 30)));

        // then
        assertThat(key).isEqualTo("ACTIVE#2026-10-13T09:05:30#e3b0c442-98fc-4c14-9afb-f4c8996fb924");
    }

    @ParameterizedTest
    @EnumSource(value = RMAStatus.class, names = {"Completed", "Rejected"})
    void closedStatusesAreClosed(RMAStatus status) {
        // when
        String key = RmaListSortKey.of(rma(status, LocalDateTime.of(2026, 10, 13, 9, 5)));

        // then
        assertThat(key).startsWith("CLOSED#2026-10-13T09:05#");
    }

    @Test
    void aRecordWithoutStatusOrDateIsActiveAndOldest() {
        // when
        String key = RmaListSortKey.of(rma(null, null));

        // then
        assertThat(key).isEqualTo("ACTIVE#0000-01-01T00:00#e3b0c442-98fc-4c14-9afb-f4c8996fb924");
    }

    @Test
    void boundsCoverWholeDaysWithTheEndDayIncluded() {
        // when
        String lo = RmaListSortKey.lowerBound(RmaListSortKey.CLOSED, LocalDate.of(2026, 10, 1));
        String hi = RmaListSortKey.upperBound(RmaListSortKey.CLOSED, LocalDate.of(2026, 10, 13));

        // then
        assertThat("CLOSED#2026-10-13T23:59:59.999#x").isBetween(lo, hi);
        assertThat("CLOSED#2026-10-01T00:00#x").isBetween(lo, hi);
        assertThat("CLOSED#2026-10-14T00:00#x").isGreaterThan(hi);
        assertThat(RmaListSortKey.upperBound(RmaListSortKey.ACTIVE, null)).isEqualTo("ACTIVE#￿");
        assertThat(RmaListSortKey.lowerBound(RmaListSortKey.ACTIVE, null)).isEqualTo("ACTIVE#");
    }

    @Test
    void theMapperWritesTheKeyComputedFromTheRecordNotAStoredOne() {
        // given
        RMA rma = rma(RMAStatus.Completed, LocalDateTime.of(2026, 10, 13, 9, 5));
        rma.setRmaListSortKey("ACTIVE#stale");
        DynamoDBMapper mapper = new DynamoDBMapper(mock(AmazonDynamoDB.class));

        // when
        var item = mapper.getTableModel(RMA.class).convert(rma);

        // then
        assertThat(item.get("rmaListSortKey").getS()).isEqualTo(RmaListSortKey.of(rma)).startsWith("CLOSED#");
    }
}
