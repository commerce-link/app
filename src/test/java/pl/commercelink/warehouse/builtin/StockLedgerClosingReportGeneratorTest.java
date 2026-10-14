package pl.commercelink.warehouse.builtin;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.reports.ReportType;
import pl.commercelink.warehouse.builtin.StockLedgerClosingResult.Blocked;
import pl.commercelink.warehouse.builtin.StockLedgerClosingResult.Closed;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockLedgerClosingReportGeneratorTest {

    private static final StockLedgerPeriod MARCH = new StockLedgerPeriod(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31));

    @Mock
    private StockLedgerPeriodClosing closing;
    @InjectMocks
    private StockLedgerClosingReportGenerator generator;

    @Test
    void theClosingIsDoneByTheClosingServiceAndLeavesNoFileOfItsOwn() throws Exception {
        // given
        when(closing.close("store-1", MARCH)).thenReturn(new Closed(MARCH, false, List.of()));

        // when
        var file = generator.generate("store-1", MARCH.from(), MARCH.to());

        // then
        assertThat(generator.type()).isEqualTo(ReportType.STOCK_LEDGER_CLOSING);
        assertThat(file).isEmpty();
        verify(closing).close("store-1", MARCH);
    }

    @Test
    void aClosingThatTurnedBlockedWhileQueuedIsSkippedWithoutFailingTheMessage() throws Exception {
        // given
        when(closing.close("store-1", MARCH)).thenReturn(new Blocked(List.of()));

        // when
        var file = generator.generate("store-1", MARCH.from(), MARCH.to());

        // then
        assertThat(file).isEmpty();
    }
}
