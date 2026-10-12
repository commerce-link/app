package pl.commercelink.web.reports;

import org.junit.jupiter.api.Test;
import pl.commercelink.warehouse.builtin.StockLedgerPeriod;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class StockLedgerClosingViewTest {

    private static final LocalDate REPORT_FROM = LocalDate.of(2026, 9, 1);
    private static final LocalDate REPORT_TO = LocalDate.of(2026, 9, 30);
    private static final Locale PL = Locale.forLanguageTag("pl");

    @Test
    void offersTheMonthAfterTheLastClosedPeriod() {
        // given
        List<StockLedgerPeriod> closed = List.of(
                new StockLedgerPeriod(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31)),
                new StockLedgerPeriod(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)));

        // when
        StockLedgerClosingView view = StockLedgerClosingView.of(closed, REPORT_FROM, REPORT_TO, PL);

        // then
        assertThat(view.dateFrom()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(view.dateTo()).isEqualTo(LocalDate.of(2026, 10, 31));
        assertThat(view.closedPeriods()).extracting(StockLedgerClosingView.ClosedPeriod::from)
                .containsExactly(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 8, 1));
    }

    @Test
    void offeredMonthStartsTheDayAfterAPeriodEndingMidMonth() {
        // given
        List<StockLedgerPeriod> closed = List.of(new StockLedgerPeriod(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 20)));

        // when
        StockLedgerClosingView view = StockLedgerClosingView.of(closed, REPORT_FROM, REPORT_TO, PL);

        // then
        assertThat(view.dateFrom()).isEqualTo(LocalDate.of(2026, 4, 21));
        assertThat(view.dateTo()).isEqualTo(LocalDate.of(2026, 5, 20));
    }

    @Test
    void offersTheReportRangeBeforeAnyClosing() {
        // when
        StockLedgerClosingView view = StockLedgerClosingView.of(List.of(), REPORT_FROM, REPORT_TO, PL);

        // then
        assertThat(view.dateFrom()).isEqualTo(REPORT_FROM);
        assertThat(view.dateTo()).isEqualTo(REPORT_TO);
    }

    @Test
    void afterAClosedPeriodTheStartIsFixedAndNamed() {
        // given
        List<StockLedgerPeriod> closed = List.of(new StockLedgerPeriod(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31)));

        // when
        StockLedgerClosingView view = StockLedgerClosingView.of(closed, REPORT_FROM, REPORT_TO, PL);

        // then
        assertThat(view.fromFixed()).isTrue();
        assertThat(view.fromLabel()).isEqualTo("1.09.2026");
        assertThat(view.previousLabel()).isEqualTo("sierpień 2026");
        assertThat(view.closedPeriods().get(0).label()).isEqualTo("sierpień 2026");
        assertThat(view.closedPeriods().get(0).regenerateHref())
                .isEqualTo("/dashboard/reports/stock-ledger/close?dateFrom=2026-08-01&dateTo=2026-08-31");
    }

    @Test
    void theFirstClosingLetsTheStartBeChosen() {
        // when
        StockLedgerClosingView view = StockLedgerClosingView.of(List.of(), REPORT_FROM, REPORT_TO, PL);

        // then
        assertThat(view.fromFixed()).isFalse();
        assertThat(view.closedPeriods()).isEmpty();
    }

    @Test
    void aRangeStartingInsideTheChainNamesTheClosedPeriodBeforeIt() {
        // given
        List<StockLedgerPeriod> closed = List.of(
                new StockLedgerPeriod(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31)),
                new StockLedgerPeriod(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)));
        StockLedgerClosingView view = StockLedgerClosingView.of(closed, REPORT_FROM, REPORT_TO, PL);

        // when
        StockLedgerClosingView september = view.withRange(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));

        // then
        assertThat(view.previousLabel()).isEqualTo("wrzesień 2026");
        assertThat(september.previousLabel()).isEqualTo("sierpień 2026");
    }
}
