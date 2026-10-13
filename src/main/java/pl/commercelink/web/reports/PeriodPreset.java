package pl.commercelink.web.reports;

import java.time.LocalDate;

/** The shortcuts of the period menu, counted from today. */
public enum PeriodPreset {
    THIS_MONTH("reports.period.preset.thisMonth"),
    PREVIOUS_MONTH("reports.period.preset.previousMonth"),
    THIS_QUARTER("reports.period.preset.thisQuarter"),
    PREVIOUS_QUARTER("reports.period.preset.previousQuarter"),
    THIS_YEAR("reports.period.preset.thisYear"),
    PREVIOUS_YEAR("reports.period.preset.previousYear");

    private final String messageKey;

    PeriodPreset(String messageKey) {
        this.messageKey = messageKey;
    }

    public String messageKey() {
        return messageKey;
    }

    public ReportPeriod period(LocalDate today) {
        LocalDate quarterStart = today.withDayOfMonth(1).withMonth((today.getMonthValue() - 1) / 3 * 3 + 1);
        LocalDate yearStart = today.withDayOfYear(1);
        return switch (this) {
            case THIS_MONTH -> ReportPeriod.month(today);
            case PREVIOUS_MONTH -> ReportPeriod.month(today.minusMonths(1));
            case THIS_QUARTER -> new ReportPeriod(quarterStart, quarterStart.plusMonths(3).minusDays(1));
            case PREVIOUS_QUARTER -> new ReportPeriod(quarterStart.minusMonths(3), quarterStart.minusDays(1));
            case THIS_YEAR -> new ReportPeriod(yearStart, yearStart.plusYears(1).minusDays(1));
            case PREVIOUS_YEAR -> new ReportPeriod(yearStart.minusYears(1), yearStart.minusDays(1));
        };
    }
}
