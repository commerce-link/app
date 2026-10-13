package pl.commercelink.web.reports;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;

import java.time.LocalDate;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class ReportPeriodTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 12);
    private static final Locale PL = Locale.forLanguageTag("pl");

    @Test
    void withoutDatesTheReportCoversThePreviousMonth() {
        // when
        ReportPeriod period = ReportPeriod.of(null, LocalDate.of(2026, 9, 5), TODAY);

        // then
        assertThat(period).isEqualTo(new ReportPeriod(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)));
    }

    @Test
    void swappedDatesAreTurnedAround() {
        // when
        ReportPeriod period = ReportPeriod.of(LocalDate.of(2026, 9, 30), LocalDate.of(2026, 9, 1), TODAY);

        // then
        assertThat(period).isEqualTo(new ReportPeriod(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)));
    }

    @Test
    void aFullMonthMovesByAMonthAcrossTheYearEnd() {
        // given
        ReportPeriod december = new ReportPeriod(LocalDate.of(2025, 12, 1), LocalDate.of(2025, 12, 31));
        ReportPeriod february = new ReportPeriod(LocalDate.of(2024, 2, 1), LocalDate.of(2024, 2, 29));

        // then
        assertThat(december.next()).isEqualTo(new ReportPeriod(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)));
        assertThat(december.previous()).isEqualTo(new ReportPeriod(LocalDate.of(2025, 11, 1), LocalDate.of(2025, 11, 30)));
        assertThat(february.next()).isEqualTo(new ReportPeriod(LocalDate.of(2024, 3, 1), LocalDate.of(2024, 3, 31)));
    }

    @Test
    void aFullQuarterAndYearMoveByAQuarterAndAYear() {
        // given
        ReportPeriod q4 = new ReportPeriod(LocalDate.of(2025, 10, 1), LocalDate.of(2025, 12, 31));
        ReportPeriod year = new ReportPeriod(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31));

        // then
        assertThat(q4.next()).isEqualTo(new ReportPeriod(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31)));
        assertThat(year.previous()).isEqualTo(new ReportPeriod(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31)));
    }

    @Test
    void anyOtherRangeMovesByItsOwnLength() {
        // given
        ReportPeriod firstHalf = new ReportPeriod(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 15));

        // then
        assertThat(firstHalf.next()).isEqualTo(new ReportPeriod(LocalDate.of(2026, 9, 16), LocalDate.of(2026, 9, 30)));
        assertThat(firstHalf.previous()).isEqualTo(new ReportPeriod(LocalDate.of(2026, 8, 17), LocalDate.of(2026, 8, 31)));
    }

    @Test
    void aSingleDayIsPrintedOnce() {
        // then
        assertThat(new ReportPeriod(LocalDate.of(2026, 9, 5), LocalDate.of(2026, 9, 5)).rangeText()).isEqualTo("5.09.2026");
    }

    @Test
    void rangeTextDropsWhatBothEndsShare() {
        // then
        assertThat(new ReportPeriod(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)).rangeText()).isEqualTo("1–30.09.2026");
        assertThat(new ReportPeriod(LocalDate.of(2026, 9, 28), LocalDate.of(2026, 10, 5)).rangeText()).isEqualTo("28.09–5.10.2026");
        assertThat(new ReportPeriod(LocalDate.of(2025, 12, 30), LocalDate.of(2026, 1, 2)).rangeText()).isEqualTo("30.12.2025–2.01.2026");
    }

    @Test
    void labelNamesTheMonthQuarterOrYear() {
        // given
        StaticMessageSource messages = new StaticMessageSource();
        messages.addMessage("reports.period.quarter", PL, "{1} kwartał {2}");

        // then
        assertThat(new ReportPeriod(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)).label(PL, messages)).isEqualTo("wrzesień 2026");
        assertThat(new ReportPeriod(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 9, 30)).label(PL, messages)).isEqualTo("III kwartał 2026");
        assertThat(new ReportPeriod(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)).label(PL, messages)).isEqualTo("2026");
        assertThat(new ReportPeriod(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 15)).label(PL, messages)).isEqualTo("1–15.09.2026");
        assertThat(new ReportPeriod(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)).label(Locale.ENGLISH, messages)).isEqualTo("September 2026");
    }

    @Test
    void presetsAreCountedFromToday() {
        // then
        assertThat(PeriodPreset.PREVIOUS_QUARTER.period(TODAY))
                .isEqualTo(new ReportPeriod(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 9, 30)));
        assertThat(PeriodPreset.THIS_YEAR.period(TODAY))
                .isEqualTo(new ReportPeriod(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)));
        assertThat(PeriodPreset.PREVIOUS_MONTH.period(LocalDate.of(2026, 1, 15)))
                .isEqualTo(new ReportPeriod(LocalDate.of(2025, 12, 1), LocalDate.of(2025, 12, 31)));
    }

    @Test
    void thePeriodAfterTodayIsNotOffered() {
        // then
        assertThat(new ReportPeriod(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)).next().startsAfter(TODAY)).isTrue();
        assertThat(new ReportPeriod(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)).next().startsAfter(TODAY)).isFalse();
    }

    @Test
    void aRangeLongerThanTheLongestReportKeepsItsEndAndIsShortened() {
        // when
        ReportPeriod mistyped = ReportPeriod.of(LocalDate.of(202, 9, 1), LocalDate.of(2026, 9, 30), TODAY);
        ReportPeriod swapped = ReportPeriod.of(LocalDate.of(9999, 12, 31), LocalDate.of(1, 1, 1), TODAY);

        // then
        assertThat(mistyped).isEqualTo(new ReportPeriod(LocalDate.of(2021, 10, 1), LocalDate.of(2026, 9, 30)));
        assertThat(swapped.length()).isLessThanOrEqualTo(366L * ReportPeriod.MAX_YEARS);
        assertThat(ReportPeriod.tooLong(LocalDate.of(202, 9, 1), LocalDate.of(2026, 9, 30))).isTrue();
        assertThat(ReportPeriod.tooLong(LocalDate.of(2021, 10, 1), LocalDate.of(2026, 9, 30))).isFalse();
        assertThat(ReportPeriod.tooLong(LocalDate.of(2026, 9, 30), LocalDate.of(2021, 10, 1))).isFalse();
    }

    @Test
    void wholeMonthsStepByCalendarMonthsAcrossLeapYearsAndYearEnds() {
        // given
        ReportPeriod march2024 = new ReportPeriod(LocalDate.of(2024, 3, 1), LocalDate.of(2024, 3, 31));
        ReportPeriod firstQuarter = new ReportPeriod(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31));
        ReportPeriod year = new ReportPeriod(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
        ReportPeriod twoYears = new ReportPeriod(LocalDate.of(2025, 1, 1), LocalDate.of(2026, 12, 31));

        // then
        assertThat(march2024.previous()).isEqualTo(new ReportPeriod(LocalDate.of(2024, 2, 1), LocalDate.of(2024, 2, 29)));
        assertThat(firstQuarter.previous()).isEqualTo(new ReportPeriod(LocalDate.of(2025, 10, 1), LocalDate.of(2025, 12, 31)));
        assertThat(year.next()).isEqualTo(new ReportPeriod(LocalDate.of(2027, 1, 1), LocalDate.of(2027, 12, 31)));
        assertThat(twoYears.previous()).isEqualTo(new ReportPeriod(LocalDate.of(2023, 1, 1), LocalDate.of(2024, 12, 31)));
        assertThat(twoYears.next()).isEqualTo(new ReportPeriod(LocalDate.of(2027, 1, 1), LocalDate.of(2028, 12, 31)));
    }
}
