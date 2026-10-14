package pl.commercelink.reports;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Optional;

/**
 * One kind of report generated from the queue; a new report is one more implementation and a {@link ReportType}.
 * It returns the file to keep for download, or nothing when it stores its result itself.
 */
public interface ReportGenerator {

    ReportType type();

    Optional<byte[]> generate(String storeId, LocalDate dateFrom, LocalDate dateTo) throws IOException;
}
