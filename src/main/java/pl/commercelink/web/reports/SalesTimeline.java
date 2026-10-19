package pl.commercelink.web.reports;

import pl.commercelink.financials.DailySales;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Groups the report's days into the columns of the daily chart: days up to two months, then weeks, then months. */
public final class SalesTimeline {

    public enum Granularity { DAY, WEEK, MONTH }

    public record Bucket(LocalDate start, LocalDate end, double ownSales, double marketplaceSales,
                         double ownProfit, double marketplaceProfit, int ownOrders, int marketplaceOrders) {

        Bucket plus(DailySales day) {
            return new Bucket(start, day.day().isAfter(end) ? day.day() : end,
                    ownSales + day.ownPriceNet(), marketplaceSales + day.marketplacePriceNet(),
                    ownProfit + day.ownProfitNet(), marketplaceProfit + day.marketplaceProfitNet(),
                    ownOrders + day.ownOrders(), marketplaceOrders + day.marketplaceOrders());
        }
    }

    private SalesTimeline() {
    }

    public static Granularity granularity(long days) {
        return days <= 62 ? Granularity.DAY : days <= 366 ? Granularity.WEEK : Granularity.MONTH;
    }

    public static List<Bucket> buckets(List<DailySales> days) {
        if (days.isEmpty()) {
            return List.of();
        }
        Granularity granularity = granularity(days.size());
        Map<LocalDate, Bucket> buckets = new LinkedHashMap<>();
        for (DailySales day : days) {
            LocalDate key = switch (granularity) {
                case DAY -> day.day();
                case WEEK -> day.day().with(DayOfWeek.MONDAY);
                case MONTH -> day.day().withDayOfMonth(1);
            };
            buckets.merge(key, new Bucket(day.day(), day.day(), 0, 0, 0, 0, 0, 0).plus(day),
                    (existing, added) -> existing.plus(day));
        }
        return new ArrayList<>(buckets.values());
    }
}
