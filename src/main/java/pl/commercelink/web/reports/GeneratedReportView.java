package pl.commercelink.web.reports;

import org.springframework.context.MessageSource;
import pl.commercelink.reports.GeneratedReports;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** A generated report file as the stock ledger page lists it. */
public record GeneratedReportView(String title, String periodText, String generatedAt, String downloadHref) {

    private static final ZoneId ZONE = ZoneId.of("Europe/Warsaw");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("d.MM.yyyy HH:mm");

    public static GeneratedReportView of(GeneratedReports.Entry entry, String downloadPath, MessageSource messages, Locale locale) {
        return new GeneratedReportView(messages.getMessage(entry.type().titleKey(), null, locale),
                new ReportPeriod(entry.dateFrom(), entry.dateTo()).rangeText(),
                TIME.format(entry.generatedAt().atZone(ZONE)),
                downloadPath + "?dateFrom=" + entry.dateFrom() + "&dateTo=" + entry.dateTo());
    }
}
