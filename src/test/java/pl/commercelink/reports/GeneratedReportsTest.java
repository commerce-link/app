package pl.commercelink.reports;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.starter.storage.FileStorage;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GeneratedReportsTest {

    private static final String BUCKET = "stores";
    private static final String PREFIX = "generated-reports/store-1/stock-ledger/";
    private static final LocalDate FROM = LocalDate.of(2026, 9, 1);
    private static final LocalDate TO = LocalDate.of(2026, 9, 30);

    @Mock
    private FileStorage fileStorage;

    private GeneratedReports generatedReports;

    @BeforeEach
    void setUp() {
        generatedReports = new GeneratedReports(fileStorage, BUCKET);
    }

    @Test
    void aReportIsKeptUnderTheStoreTheTypeAndTheRange() {
        // given

        // when
        generatedReports.save("store-1", ReportType.STOCK_LEDGER_EXPORT, FROM, TO, "csv".getBytes());

        // then
        verify(fileStorage).put(org.mockito.ArgumentMatchers.eq(BUCKET), org.mockito.ArgumentMatchers.eq(PREFIX + "2026-09-01_2026-09-30.csv"), aryEq("csv".getBytes()));
    }

    @Test
    void aReportThatIsThereCanBeFoundAndOneThatIsNotCannot() {
        // given
        when(fileStorage.canRead(BUCKET, PREFIX + "2026-09-01_2026-09-30.csv")).thenReturn(true);
        when(fileStorage.getBytes(BUCKET, PREFIX + "2026-09-01_2026-09-30.csv")).thenReturn("csv".getBytes());
        when(fileStorage.canRead(BUCKET, PREFIX + "2026-08-01_2026-08-31.csv")).thenReturn(false);

        // when
        Optional<byte[]> found = generatedReports.find("store-1", ReportType.STOCK_LEDGER_EXPORT, FROM, TO);
        Optional<byte[]> missing = generatedReports.find("store-1", ReportType.STOCK_LEDGER_EXPORT, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));

        // then
        assertThat(found).contains("csv".getBytes());
        assertThat(missing).isEmpty();
    }

    @Test
    void theNewestReportsComeFirstAndFilesThatAreNotReportsAreSkipped() {
        // given
        when(fileStorage.findAllKeysByKeyOrder(BUCKET, PREFIX)).thenReturn(List.of(
                PREFIX + "2026-08-01_2026-08-31.csv", PREFIX + "2026-09-01_2026-09-30.csv", PREFIX + "notes.txt", PREFIX + "x_y.csv"));
        when(fileStorage.findLastModified(BUCKET, PREFIX + "2026-08-01_2026-08-31.csv")).thenReturn(Optional.of(Instant.parse("2026-10-13T08:00:00Z")));
        when(fileStorage.findLastModified(BUCKET, PREFIX + "2026-09-01_2026-09-30.csv")).thenReturn(Optional.of(Instant.parse("2026-10-14T08:00:00Z")));

        // when
        List<GeneratedReports.Entry> recent = generatedReports.recent("store-1");

        // then
        assertThat(recent).extracting(GeneratedReports.Entry::dateFrom).containsExactly(FROM, LocalDate.of(2026, 8, 1));
        assertThat(recent.get(0).generatedAt()).isEqualTo(Instant.parse("2026-10-14T08:00:00Z"));
    }
}
