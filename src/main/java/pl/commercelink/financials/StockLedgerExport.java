package pl.commercelink.financials;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import pl.commercelink.reports.ReportGenerator;
import pl.commercelink.reports.ReportType;
import pl.commercelink.warehouse.builtin.StockLedgerRow;
import pl.commercelink.warehouse.builtin.StockLedgerService;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Optional;

@Component
public class StockLedgerExport implements ReportGenerator {

    private final StockLedgerService stockLedgerService;

    @Autowired
    public StockLedgerExport(StockLedgerService stockLedgerService) {
        this.stockLedgerService = stockLedgerService;
    }

    @Override
    public ReportType type() {
        return ReportType.STOCK_LEDGER_EXPORT;
    }

    @Override
    public Optional<byte[]> generate(String storeId, LocalDate dateFrom, LocalDate dateTo) throws IOException {
        return Optional.of(run(storeId, dateFrom, dateTo));
    }

    public byte[] run(String storeId, LocalDate dateFrom, LocalDate dateTo) throws IOException {
        return StockLedgerRow.toCsv(stockLedgerService.generate(storeId, dateFrom, dateTo));
    }
}
