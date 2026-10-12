package pl.commercelink.web.reports;

import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.ToDoubleFunction;

/** One panel of the daily chart: columns stacked store-below-marketplace on a round axis, every value as text. */
public record DailyChart(Metric metric, List<AxisTick> axis, List<Column> columns, List<String> xLabels,
                         String ownTotal, String marketplaceTotal) {

    private static final int EDGE_COLUMNS = 3;

    public enum Metric {
        SALES("reports.chart.sales", "reports.chart.segment.sales", true),
        PROFIT("reports.chart.profit", "reports.chart.segment.profit", true),
        ORDERS("reports.chart.orders", "reports.chart.segment.orders", false);

        private final String titleKey;
        private final String segmentKey;
        private final boolean money;

        Metric(String titleKey, String segmentKey, boolean money) {
            this.titleKey = titleKey;
            this.segmentKey = segmentKey;
            this.money = money;
        }

        public String titleKey() {
            return titleKey;
        }

        public String segmentKey() {
            return segmentKey;
        }

        public boolean money() {
            return money;
        }

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public record AxisTick(String number, String unitKey, double bottom) {
    }

    public record Column(String label, String own, String marketplace, String total,
                         double ownHeight, double marketplaceHeight, String edge) {
    }

    public static DailyChart of(Metric metric, List<SalesTimeline.Bucket> buckets, SalesTimeline.Granularity granularity, Locale locale) {
        ToDoubleFunction<SalesTimeline.Bucket> own = switch (metric) {
            case SALES -> SalesTimeline.Bucket::ownSales;
            case PROFIT -> SalesTimeline.Bucket::ownProfit;
            case ORDERS -> SalesTimeline.Bucket::ownOrders;
        };
        ToDoubleFunction<SalesTimeline.Bucket> marketplace = switch (metric) {
            case SALES -> SalesTimeline.Bucket::marketplaceSales;
            case PROFIT -> SalesTimeline.Bucket::marketplaceProfit;
            case ORDERS -> SalesTimeline.Bucket::marketplaceOrders;
        };
        double max = buckets.stream()
                .mapToDouble(bucket -> Math.max(0, own.applyAsDouble(bucket)) + Math.max(0, marketplace.applyAsDouble(bucket)))
                .max().orElse(0);
        ChartScale scale = ChartScale.fitting(max, !metric.money());
        List<AxisTick> axis = scale.ticks().stream().map(tick -> {
            ReportNumbers.Axis value = metric.money() ? ReportNumbers.axis(tick) : new ReportNumbers.Axis(String.valueOf(Math.round(tick)), null);
            return new AxisTick(value.number(), value.unitKey(), scale.percent(tick));
        }).toList();
        List<Column> columns = new ArrayList<>();
        for (int i = 0; i < buckets.size(); i++) {
            SalesTimeline.Bucket bucket = buckets.get(i);
            double ownValue = own.applyAsDouble(bucket);
            double marketplaceValue = marketplace.applyAsDouble(bucket);
            String edge = i < EDGE_COLUMNS ? "is-first" : i >= buckets.size() - EDGE_COLUMNS ? "is-last" : null;
            columns.add(new Column(label(bucket, granularity, locale), text(metric, ownValue), text(metric, marketplaceValue),
                    text(metric, ownValue + marketplaceValue), scale.percent(ownValue), scale.percent(marketplaceValue), edge));
        }
        double ownSum = buckets.stream().mapToDouble(own).sum();
        double marketplaceSum = buckets.stream().mapToDouble(marketplace).sum();
        return new DailyChart(metric, axis, columns, xLabels(columns), total(metric, ownSum), total(metric, marketplaceSum));
    }

    public boolean empty() {
        return columns.stream().allMatch(column -> column.ownHeight() == 0 && column.marketplaceHeight() == 0)
                && "0".equals(ownTotal) && "0".equals(marketplaceTotal);
    }

    private static String text(Metric metric, double value) {
        return metric.money() ? ReportNumbers.amount(value) : String.valueOf(Math.round(value));
    }

    private static String total(Metric metric, double value) {
        return metric.money() ? ReportNumbers.wholeAmount(value) : String.valueOf(Math.round(value));
    }

    private static String label(SalesTimeline.Bucket bucket, SalesTimeline.Granularity granularity, Locale locale) {
        LocalDate start = bucket.start();
        LocalDate end = bucket.end();
        return switch (granularity) {
            case DAY -> start.getDayOfMonth() + "." + twoDigits(start.getMonthValue());
            case WEEK -> start.getMonthValue() == end.getMonthValue()
                    ? start.getDayOfMonth() + "–" + end.getDayOfMonth() + "." + twoDigits(end.getMonthValue())
                    : start.getDayOfMonth() + "." + twoDigits(start.getMonthValue()) + "–" + end.getDayOfMonth() + "." + twoDigits(end.getMonthValue());
            case MONTH -> start.getMonth().getDisplayName(TextStyle.SHORT_STANDALONE, locale) + " " + start.getYear();
        };
    }

    private static List<String> xLabels(List<Column> columns) {
        if (columns.isEmpty()) {
            return List.of();
        }
        int last = columns.size() - 1;
        return List.of(0, last / 4, last / 2, last * 3 / 4, last).stream().distinct().map(i -> columns.get(i).label()).toList();
    }

    private static String twoDigits(int value) {
        return value < 10 ? "0" + value : String.valueOf(value);
    }
}
