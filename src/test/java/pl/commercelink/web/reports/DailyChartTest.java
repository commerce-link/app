package pl.commercelink.web.reports;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class DailyChartTest {

    private static final Locale PL = Locale.forLanguageTag("pl");

    @Test
    void columnsStackTheChannelsOnARoundAxis() {
        // given
        List<SalesTimeline.Bucket> buckets = buckets(30);

        // when
        DailyChart chart = DailyChart.of(DailyChart.Metric.SALES, buckets, SalesTimeline.Granularity.DAY, PL);

        // then
        assertThat(chart.columns()).hasSize(30);
        assertThat(chart.axis()).hasSize(5);
        assertThat(chart.axis().get(4).bottom()).isEqualTo(100);
        DailyChart.Column first = chart.columns().get(0);
        assertThat(first.label()).isEqualTo("1.09");
        assertThat(first.own()).isEqualTo("1 000,00");
        assertThat(first.edge()).isEqualTo("is-first");
        assertThat(chart.columns().get(29).edge()).isEqualTo("is-last");
        assertThat(chart.columns().get(15).edge()).isNull();
        assertThat(first.ownHeight() + first.marketplaceHeight()).isLessThanOrEqualTo(100);
        assertThat(chart.xLabels()).hasSize(5).first().isEqualTo("1.09");
        assertThat(chart.ownTotal()).isEqualTo("30 000");
    }

    @Test
    void monthlyColumnsAreLabelledWithTheShortMonthAndTheYear() {
        // given
        List<SalesTimeline.Bucket> buckets = List.of(
                new SalesTimeline.Bucket(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), 100, 0, 10, 0, 1, 0));

        // when
        DailyChart chart = DailyChart.of(DailyChart.Metric.SALES, buckets, SalesTimeline.Granularity.MONTH, PL);

        // then
        assertThat(chart.columns().get(0).label()).isEqualTo("wrz 2026");
    }

    @Test
    void ordersAreCountsAndANegativeProfitHasNoHeight() {
        // given
        List<SalesTimeline.Bucket> buckets = List.of(
                new SalesTimeline.Bucket(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 1), 100, 0, -50, 0, 2, 0));

        // when
        DailyChart orders = DailyChart.of(DailyChart.Metric.ORDERS, buckets, SalesTimeline.Granularity.DAY, PL);
        DailyChart profit = DailyChart.of(DailyChart.Metric.PROFIT, buckets, SalesTimeline.Granularity.DAY, PL);

        // then
        assertThat(orders.columns().get(0).own()).isEqualTo("2");
        assertThat(profit.columns().get(0).ownHeight()).isZero();
        assertThat(profit.columns().get(0).own()).isEqualTo("−50,00");
    }

    @Test
    void weeksAreLabelledWithTheirDays() {
        // given
        List<SalesTimeline.Bucket> buckets = List.of(
                new SalesTimeline.Bucket(LocalDate.of(2026, 9, 28), LocalDate.of(2026, 10, 4), 1, 1, 1, 1, 1, 1));

        // then
        assertThat(DailyChart.of(DailyChart.Metric.SALES, buckets, SalesTimeline.Granularity.WEEK, PL).columns().get(0).label())
                .isEqualTo("28.09–4.10");
    }

    @Test
    void aPeriodWithoutOrdersIsEmpty() {
        // given
        List<SalesTimeline.Bucket> buckets = List.of(
                new SalesTimeline.Bucket(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 1), 0, 0, 0, 0, 0, 0));

        // then
        assertThat(DailyChart.of(DailyChart.Metric.SALES, buckets, SalesTimeline.Granularity.DAY, PL).empty()).isTrue();
    }

    private static List<SalesTimeline.Bucket> buckets(int days) {
        List<SalesTimeline.Bucket> buckets = new ArrayList<>();
        for (int i = 0; i < days; i++) {
            LocalDate day = LocalDate.of(2026, 9, 1).plusDays(i);
            buckets.add(new SalesTimeline.Bucket(day, day, 1_000, 2_000, 100, 200, 1, 2));
        }
        return buckets;
    }
}
