package pl.commercelink.web.reports;

import org.springframework.context.MessageSource;

import java.time.LocalDate;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

/** The date range of the reports page, both ends included. */
public record ReportPeriod(LocalDate from, LocalDate to) {

    private static final String[] ROMAN = {"I", "II", "III", "IV"};

    /** The page draws a column per day, week or month of the range; a typo in a year must not make it draw thousands. */
    public static final int MAX_YEARS = 5;

    /**
     * No range asked for opens the previous month, the period most often settled with the accountant. A range longer
     * than {@link #MAX_YEARS} keeps its end and is shortened to that many years.
     */
    public static ReportPeriod of(LocalDate from, LocalDate to, LocalDate today) {
        if (from == null || to == null) {
            return month(today.minusMonths(1));
        }
        LocalDate start = from.isAfter(to) ? to : from;
        LocalDate end = from.isAfter(to) ? from : to;
        return tooLong(start, end) ? new ReportPeriod(end.minusYears(MAX_YEARS).plusDays(1), end) : new ReportPeriod(start, end);
    }

    /** Whether the asked range, in either order, is longer than the page shows. */
    public static boolean tooLong(LocalDate from, LocalDate to) {
        LocalDate start = from.isAfter(to) ? to : from;
        LocalDate end = from.isAfter(to) ? from : to;
        return start.isBefore(end.minusYears(MAX_YEARS).plusDays(1));
    }

    static ReportPeriod month(LocalDate day) {
        LocalDate start = day.withDayOfMonth(1);
        return new ReportPeriod(start, start.withDayOfMonth(start.lengthOfMonth()));
    }

    public boolean isFullMonth() {
        return from.getDayOfMonth() == 1 && to.equals(from.plusMonths(1).minusDays(1));
    }

    public boolean isFullQuarter() {
        return from.getDayOfMonth() == 1 && (from.getMonthValue() - 1) % 3 == 0 && to.equals(from.plusMonths(3).minusDays(1));
    }

    public boolean isFullYear() {
        return from.getDayOfYear() == 1 && to.equals(from.plusYears(1).minusDays(1));
    }

    public long length() {
        return ChronoUnit.DAYS.between(from, to) + 1;
    }

    // whole calendar months (a month, a quarter, a year, two years) step by as many months, so a step never lands on
    // "2.01–31.12" because of a leap year; any other range steps by its length in days
    public ReportPeriod previous() {
        if (isWholeMonths()) {
            return new ReportPeriod(from.minusMonths(months()), from.minusDays(1));
        }
        return new ReportPeriod(from.minusDays(length()), from.minusDays(1));
    }

    public ReportPeriod next() {
        if (isWholeMonths()) {
            LocalDate start = to.plusDays(1);
            return new ReportPeriod(start, start.plusMonths(months()).minusDays(1));
        }
        return new ReportPeriod(to.plusDays(1), to.plusDays(length()));
    }

    private boolean isWholeMonths() {
        return from.getDayOfMonth() == 1 && to.plusDays(1).getDayOfMonth() == 1;
    }

    private long months() {
        return ChronoUnit.MONTHS.between(from, to.plusDays(1));
    }

    public boolean startsAfter(LocalDate day) {
        return from.isAfter(day);
    }

    /** "5.09.2026", "1–30.09.2026", "28.09–5.10.2026", "30.12.2025–2.01.2026": what both ends share is written once. */
    public String rangeText() {
        String end = to.getDayOfMonth() + "." + twoDigits(to.getMonthValue()) + "." + to.getYear();
        if (from.equals(to)) {
            return end;
        }
        if (from.getYear() != to.getYear()) {
            return from.getDayOfMonth() + "." + twoDigits(from.getMonthValue()) + "." + from.getYear() + "–" + end;
        }
        if (from.getMonthValue() != to.getMonthValue()) {
            return from.getDayOfMonth() + "." + twoDigits(from.getMonthValue()) + "–" + end;
        }
        return from.getDayOfMonth() + "–" + end;
    }

    public String label(Locale locale, MessageSource messages) {
        if (isFullMonth()) {
            return from.getMonth().getDisplayName(TextStyle.FULL_STANDALONE, locale) + " " + from.getYear();
        }
        if (isFullQuarter()) {
            int quarter = (from.getMonthValue() - 1) / 3 + 1;
            return messages.getMessage("reports.period.quarter",
                    new Object[]{quarter, ROMAN[quarter - 1], String.valueOf(from.getYear())}, locale);
        }
        if (isFullYear()) {
            return String.valueOf(from.getYear());
        }
        return rangeText();
    }

    public String query() {
        return "dateFrom=" + from + "&dateTo=" + to;
    }

    private static String twoDigits(int value) {
        return value < 10 ? "0" + value : String.valueOf(value);
    }
}
