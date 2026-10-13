package pl.commercelink.web.reports;

import org.junit.jupiter.api.Test;
import pl.commercelink.warehouse.builtin.StockLedgerPeriod;

import java.time.LocalDate;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class StockLedgerPeriodLabelTest {

    @Test
    void aFullMonthIsNamedAndAnyOtherRangeIsWrittenOut() {
        // then
        assertThat(StockLedgerPeriodLabel.of(new StockLedgerPeriod(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)), Locale.forLanguageTag("pl")))
                .isEqualTo("wrzesień 2026");
        assertThat(StockLedgerPeriodLabel.of(new StockLedgerPeriod(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)), Locale.ENGLISH))
                .isEqualTo("September 2026");
        assertThat(StockLedgerPeriodLabel.of(new StockLedgerPeriod(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30)), Locale.forLanguageTag("pl")))
                .isEqualTo("1.01–30.06.2026");
    }
}
