package pl.commercelink.financials;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.commercelink.starter.csv.CSVWriter;
import pl.commercelink.warehouse.builtin.ProductWeightOriginComplianceReportRow;
import pl.commercelink.warehouse.builtin.ProductWeightOriginComplianceReportService;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ProductWeightOriginComplianceReportExport {

    private final ProductWeightOriginComplianceReportService service;

    public byte[] run(String storeId, LocalDate dateFrom, LocalDate dateTo) throws IOException {
        List<ProductWeightOriginComplianceReportRow> rows = service.generate(storeId, dateFrom, dateTo);
        return new CSVWriter().writeAllRowsToBytes(rows, ProductWeightOriginComplianceReportRow.headers());
    }
}
