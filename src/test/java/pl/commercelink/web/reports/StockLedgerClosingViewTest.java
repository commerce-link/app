package pl.commercelink.web.reports;

import org.junit.jupiter.api.Test;
import pl.commercelink.warehouse.builtin.StockLedgerPeriod;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StockLedgerClosingViewTest {

    private static final LocalDate REPORT_FROM = LocalDate.of(2026, 9, 1);
    private static final LocalDate REPORT_TO = LocalDate.of(2026, 9, 30);

    @Test
    void offersTheMonthAfterTheLastClosedPeriod() {
        // given
        List<StockLedgerPeriod> closed = List.of(
                new StockLedgerPeriod(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31)),
                new StockLedgerPeriod(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)));

        // when
        StockLedgerClosingView view = StockLedgerClosingView.of(closed, REPORT_FROM, REPORT_TO);

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
        StockLedgerClosingView view = StockLedgerClosingView.of(closed, REPORT_FROM, REPORT_TO);

        // then
        assertThat(view.dateFrom()).isEqualTo(LocalDate.of(2026, 4, 21));
        assertThat(view.dateTo()).isEqualTo(LocalDate.of(2026, 5, 20));
    }

    @Test
    void offersTheReportRangeBeforeAnyClosing() {
        // when
        StockLedgerClosingView view = StockLedgerClosingView.of(List.of(), REPORT_FROM, REPORT_TO);

        // then
        assertThat(view.dateFrom()).isEqualTo(REPORT_FROM);
        assertThat(view.dateTo()).isEqualTo(REPORT_TO);
    }
}
