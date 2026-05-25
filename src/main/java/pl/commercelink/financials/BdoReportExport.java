package pl.commercelink.financials;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.commercelink.starter.csv.CSVWriter;
import pl.commercelink.warehouse.builtin.BdoReportRow;
import pl.commercelink.warehouse.builtin.BdoReportService;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

@Component
@RequiredArgsConstructor
public class BdoReportExport {

    private final BdoReportService service;

    public byte[] run(String storeId, LocalDate dateFrom, LocalDate dateTo) throws IOException {
        List<BdoReportRow> rows = service.generate(storeId, dateFrom, dateTo);
        return new CSVWriter().writeAllRowsToBytes(rows, BdoReportRow.headers());
    }
}
