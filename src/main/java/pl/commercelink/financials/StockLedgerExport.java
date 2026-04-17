package pl.commercelink.financials;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import pl.commercelink.starter.csv.CSVWriter;
import pl.commercelink.warehouse.builtin.StockLedgerRow;
import pl.commercelink.warehouse.builtin.StockLedgerService;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

@Component
public class StockLedgerExport {

    private final StockLedgerService stockLedgerService;

    @Autowired
    public StockLedgerExport(StockLedgerService stockLedgerService) {
        this.stockLedgerService = stockLedgerService;
    }

    public byte[] run(String storeId, LocalDate dateFrom, LocalDate dateTo) throws IOException {
        List<StockLedgerRow> rows = stockLedgerService.generate(storeId, dateFrom, dateTo);
        return new CSVWriter().writeAllRowsToBytes(rows, StockLedgerRow.headers());
    }
}
