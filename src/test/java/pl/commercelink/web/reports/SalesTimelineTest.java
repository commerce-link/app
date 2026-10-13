package pl.commercelink.web.reports;

import org.junit.jupiter.api.Test;
import pl.commercelink.financials.DailySales;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SalesTimelineTest {

    @Test
    void granularityFollowsTheLengthOfTheRange() {
        // then
        assertThat(SalesTimeline.granularity(62)).isEqualTo(SalesTimeline.Granularity.DAY);
        assertThat(SalesTimeline.granularity(63)).isEqualTo(SalesTimeline.Granularity.WEEK);
        assertThat(SalesTimeline.granularity(366)).isEqualTo(SalesTimeline.Granularity.WEEK);
        assertThat(SalesTimeline.granularity(367)).isEqualTo(SalesTimeline.Granularity.MONTH);
    }

    @Test
    void weeksStartOnMondayAndAreCutToTheRange() {
        // given 2026-09-03 is a Thursday; 70 days -> weeks
        List<DailySales> days = days(LocalDate.of(2026, 9, 3), 70);

        // when
        List<SalesTimeline.Bucket> buckets = SalesTimeline.buckets(days);

        // then
        assertThat(buckets.get(0).start()).isEqualTo(LocalDate.of(2026, 9, 3));
        assertThat(buckets.get(0).end()).isEqualTo(LocalDate.of(2026, 9, 6));
        assertThat(buckets.get(1).start()).isEqualTo(LocalDate.of(2026, 9, 7));
        assertThat(buckets.get(buckets.size() - 1).end()).isEqualTo(LocalDate.of(2026, 11, 11));
        assertThat(buckets.get(0).ownOrders()).isEqualTo(4);
        assertThat(buckets.stream().mapToInt(SalesTimeline.Bucket::ownOrders).sum()).isEqualTo(70);
    }

    @Test
    void aShortRangeKeepsEveryDayIncludingEmptyOnes() {
        // given
        List<DailySales> days = new ArrayList<>(days(LocalDate.of(2026, 9, 1), 3));
        days.set(1, new DailySales(LocalDate.of(2026, 9, 2), 0, 0, 0, 0, 0, 0));

        // when
        List<SalesTimeline.Bucket> buckets = SalesTimeline.buckets(days);

        // then
        assertThat(buckets).hasSize(3);
        assertThat(buckets.get(1).ownOrders()).isZero();
    }

    private static List<DailySales> days(LocalDate first, int count) {
        List<DailySales> days = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            days.add(new DailySales(first.plusDays(i), 100, 10, 1, 200, 20, 2));
        }
        return days;
    }

    @Test
    void aRangeLongerThanAYearIsCutIntoCalendarMonths() {
        // given
        List<DailySales> days = days(LocalDate.of(2025, 1, 15), 400);

        // when
        List<SalesTimeline.Bucket> buckets = SalesTimeline.buckets(days);

        // then
        assertThat(buckets).hasSize(14);
        assertThat(buckets.get(0).start()).isEqualTo(LocalDate.of(2025, 1, 15));
        assertThat(buckets.get(0).end()).isEqualTo(LocalDate.of(2025, 1, 31));
        assertThat(buckets.get(0).ownOrders()).isEqualTo(17);
        assertThat(buckets.get(1).start()).isEqualTo(LocalDate.of(2025, 2, 1));
        assertThat(buckets.get(1).end()).isEqualTo(LocalDate.of(2025, 2, 28));
    }
}
