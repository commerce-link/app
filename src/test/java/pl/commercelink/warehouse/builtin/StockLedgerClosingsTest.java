package pl.commercelink.warehouse.builtin;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.starter.storage.FileStorage;
import pl.commercelink.warehouse.builtin.StockLedgerClosings.ClosingBalance;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockLedgerClosingsTest {

    private static final String BUCKET = "stores";
    private static final String STORE_ID = "store-1";
    private static final StockLedgerPeriod SEPTEMBER = new StockLedgerPeriod(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));
    private static final String SEPTEMBER_KEY = "store-1/stock-ledger/2026-09-01_2026-09-30.csv";

    @Mock
    private FileStorage fileStorage;

    private StockLedgerClosings closings;

    @BeforeEach
    void setUp() {
        closings = new StockLedgerClosings(fileStorage, BUCKET);
    }

    @Test
    void closedPeriodsAreThePeriodFilesOfTheStoreByTheirEnd() {
        // given
        when(fileStorage.findAllKeysByKeyOrder(BUCKET, "store-1/stock-ledger/")).thenReturn(List.of(
                "store-1/stock-ledger/2026-09-01_2026-09-30.csv",
                "store-1/stock-ledger/2026-08-01_2026-08-15.csv",
                "store-1/stock-ledger/2026-08-31_2026-08-01.csv",
                "store-1/stock-ledger/2026-09.csv",
                "store-1/stock-ledger/notes.txt"));

        // when
        List<StockLedgerPeriod> closed = closings.closedPeriods(STORE_ID);

        // then
        assertThat(closed).containsExactly(
                new StockLedgerPeriod(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 15)), SEPTEMBER);
    }

    @Test
    void closingBalancesAreTheClosingColumnsOfTheSavedReport() throws Exception {
        // given
        StockLedgerRow row = new StockLedgerRow("MFN-A", "Kabel; \"HDMI\" 2m", 2, 100.0,
                Map.of(LedgerCategory.PURCHASE, 3), Map.of(LedgerCategory.PURCHASE, 150.25));
        when(fileStorage.canRead(BUCKET, SEPTEMBER_KEY)).thenReturn(true);
        when(fileStorage.getBytes(BUCKET, SEPTEMBER_KEY)).thenReturn(StockLedgerRow.toCsv(List.of(row)));

        // when
        Map<String, ClosingBalance> balances = closings.closingBalances(STORE_ID, SEPTEMBER);

        // then
        assertThat(balances).containsExactly(entry("MFN-A", new ClosingBalance("Kabel; \"HDMI\" 2m", 5, 250.25)));
    }

    @Test
    void periodWithoutAFileHasNoReport() {
        // given
        when(fileStorage.canRead(BUCKET, SEPTEMBER_KEY)).thenReturn(false);

        // when / then
        assertThat(closings.find(STORE_ID, SEPTEMBER)).isEmpty();
        assertThatThrownBy(() -> closings.closingBalances(STORE_ID, SEPTEMBER)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void reportWithoutTheClosingColumnsIsRejected() {
        // given
        byte[] report = "\"SKU (MFN)\";\"Nazwa\"\n\"MFN-A\";\"Widget\"\n".getBytes();

        // when / then
        assertThatThrownBy(() -> StockLedgerClosings.parse(report)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void reportIsSavedAsTheFileOfThePeriod() {
        // given
        byte[] report = {1, 2, 3};

        // when
        closings.save(STORE_ID, SEPTEMBER, report);

        // then
        verify(fileStorage).put(BUCKET, SEPTEMBER_KEY, report);
    }
}
