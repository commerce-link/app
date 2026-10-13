package pl.commercelink.web.reports;

import org.junit.jupiter.api.Test;
import pl.commercelink.warehouse.builtin.StockLedgerPeriod;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class StockLedgerClosingShortcutTest {

    private static final Locale PL = Locale.forLanguageTag("pl");
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 12);
    private static final StockLedgerPeriod AUGUST = new StockLedgerPeriod(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));
    private static final StockLedgerPeriod SEPTEMBER = new StockLedgerPeriod(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));

    @Test
    void aMonthThatHasEndedAndIsNotClosedIsPending() {
        // when
        StockLedgerClosingShortcut shortcut = StockLedgerClosingShortcut.of(List.of(AUGUST), TODAY, PL);

        // then
        assertThat(shortcut.pending()).isTrue();
        assertThat(shortcut.month()).isEqualTo("wrzesień");
        assertThat(shortcut.lastClosed()).isEqualTo("sierpień 2026");
    }

    @Test
    void severalMonthsBehindPointsAtTheFirstOneToClose() {
        // when
        StockLedgerClosingShortcut shortcut = StockLedgerClosingShortcut.of(
                List.of(new StockLedgerPeriod(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30))), TODAY, PL);

        // then
        assertThat(shortcut.month()).isEqualTo("lipiec");
    }

    @Test
    void nothingIsPendingWhileTheNextPeriodLasts() {
        // when
        StockLedgerClosingShortcut shortcut = StockLedgerClosingShortcut.of(List.of(AUGUST, SEPTEMBER), TODAY, PL);

        // then
        assertThat(shortcut.pending()).isFalse();
        assertThat(shortcut.lastClosed()).isEqualTo("wrzesień 2026");
    }

    @Test
    void aStoreThatNeverClosedIsNotNaggedAbout() {
        // when
        StockLedgerClosingShortcut shortcut = StockLedgerClosingShortcut.of(List.of(), TODAY, PL);

        // then
        assertThat(shortcut.pending()).isFalse();
        assertThat(shortcut.lastClosed()).isNull();
    }
}
