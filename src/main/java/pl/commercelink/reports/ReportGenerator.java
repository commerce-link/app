package pl.commercelink.reports;

import java.io.IOException;
import java.time.LocalDate;

/** One kind of report generated from the queue; a new report is one more implementation and a {@link ReportType}. */
public interface ReportGenerator {

    ReportType type();

    void generate(String storeId, LocalDate dateFrom, LocalDate dateTo) throws IOException;
}
