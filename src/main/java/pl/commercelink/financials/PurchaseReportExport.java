package pl.commercelink.financials;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.commercelink.starter.csv.CSVWriter;
import pl.commercelink.warehouse.builtin.PurchaseReportRow;
import pl.commercelink.warehouse.builtin.PurchaseReportService;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

@Component
@RequiredArgsConstructor
public class PurchaseReportExport {

    private final PurchaseReportService service;

    public byte[] run(String storeId, LocalDate dateFrom, LocalDate dateTo) throws IOException {
        List<PurchaseReportRow> rows = service.generate(storeId, dateFrom, dateTo);
        return new CSVWriter().writeAllRowsToBytes(rows, PurchaseReportRow.headers());
    }
}
