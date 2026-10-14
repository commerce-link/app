package pl.commercelink.web.reports;

import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;
import pl.commercelink.financials.FinancialReportGenerator;
import pl.commercelink.financials.FinancialReports;
import pl.commercelink.inventory.supplier.SupplierLabels;
import pl.commercelink.warehouse.builtin.StockLedgerPeriodClosing;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ReportsPageFactory {

    private static final String PATH = "/dashboard/reports";
    private static final String CLOSING_PATH = PATH + "/stock-ledger";
    /** The date fields catch a mistyped year before the request: nothing in the panel is older or more than a year ahead. */
    private static final LocalDate EARLIEST_DAY = LocalDate.of(2000, 1, 1);

    private final FinancialReportGenerator generator;
    private final SupplierLabels supplierLabels;
    private final StockLedgerPeriodClosing closing;
    private final MessageSource messages;

    public ReportsPage create(String storeId, LocalDate dateFrom, LocalDate dateTo, LocalDate today, Locale locale) {
        ReportPeriod period = ReportPeriod.of(dateFrom, dateTo, today);
        FinancialReports reports = generator.generate(storeId, period.from(), period.to());
        List<SalesTimeline.Bucket> buckets = SalesTimeline.buckets(reports.days());
        SalesTimeline.Granularity granularity = SalesTimeline.granularity(period.length());
        ReportPeriod previous = period.previous();
        ReportPeriod next = period.next();
        boolean nextAvailable = !next.startsAfter(today);
        boolean shortened = dateFrom != null && dateTo != null && ReportPeriod.tooLong(dateFrom, dateTo);
        return new ReportsPage(
                period.label(locale, messages), period.rangeText(), shortened,
                href(previous), previous.label(locale, messages),
                nextAvailable ? href(next) : null, nextAvailable ? next.label(locale, messages) : null,
                Arrays.stream(PeriodPreset.values())
                        .map(preset -> new ReportsPage.PresetLink(preset.messageKey(), href(preset.period(today)),
                                preset.period(today).equals(period)))
                        .toList(),
                period.from().toString(), period.to().toString(),
                EARLIEST_DAY.toString(), LocalDate.of(today.getYear() + 1, 12, 31).toString(), CLOSING_PATH + "?from=" + period.from() + "&to=" + period.to(),
                files(period),
                ChannelComparison.of(reports.ownSources(), reports.marketplace()),
                Arrays.stream(DailyChart.Metric.values())
                        .map(metric -> DailyChart.of(metric, buckets, granularity, locale))
                        .toList(),
                SupplierCosts.of(reports.suppliers(), supplierLabels.forStoreId(storeId)),
                StockLedgerClosingShortcut.of(closing.closedPeriods(storeId), today, locale));
    }

    private static List<ReportsPage.FileGroup> files(ReportPeriod period) {
        Map<String, List<ReportsPage.FileLink>> groups = Arrays.stream(ReportFile.values())
                .collect(Collectors.groupingBy(ReportFile::group, LinkedHashMap::new, Collectors.mapping(
                        file -> new ReportsPage.FileLink(file.titleKey(), file.descriptionKey(), file.href(period)),
                        Collectors.toList())));
        return groups.entrySet().stream().map(group -> new ReportsPage.FileGroup(group.getKey(), group.getValue())).toList();
    }

    private static String href(ReportPeriod period) {
        return PATH + "?" + period.query();
    }
}
