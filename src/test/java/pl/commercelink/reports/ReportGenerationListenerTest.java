package pl.commercelink.reports;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportGenerationListenerTest {

    private static final LocalDate FROM = LocalDate.of(2026, 9, 1);
    private static final LocalDate TO = LocalDate.of(2026, 9, 30);
    private static final ReportGenerationRequest EXPORT =
            new ReportGenerationRequest("store-1", "STOCK_LEDGER_EXPORT", "2026-09-01", "2026-09-30");

    @Mock
    private ReportGenerator exportGenerator;
    @Mock
    private ReportGenerator closingGenerator;
    @Mock
    private GeneratedReports generatedReports;

    private ReportGenerationListener listener;

    @BeforeEach
    void setUp() {
        when(exportGenerator.type()).thenReturn(ReportType.STOCK_LEDGER_EXPORT);
        when(closingGenerator.type()).thenReturn(ReportType.STOCK_LEDGER_CLOSING);
        listener = new ReportGenerationListener(new ReportGenerators(List.of(exportGenerator, closingGenerator)), generatedReports);
    }

    @Test
    void aGeneratedFileIsKept() throws Exception {
        // given
        when(exportGenerator.generate("store-1", FROM, TO)).thenReturn(Optional.of("csv".getBytes()));

        // when
        listener.handleMessage(EXPORT);

        // then
        verify(generatedReports).save(org.mockito.ArgumentMatchers.eq("store-1"), org.mockito.ArgumentMatchers.eq(ReportType.STOCK_LEDGER_EXPORT),
                org.mockito.ArgumentMatchers.eq(FROM), org.mockito.ArgumentMatchers.eq(TO), aryEq("csv".getBytes()));
    }

    @Test
    void aReportThatStoresItsOwnResultKeepsNoFile() throws Exception {
        // given
        when(closingGenerator.generate("store-1", FROM, TO)).thenReturn(Optional.empty());

        // when
        listener.handleMessage(new ReportGenerationRequest("store-1", "STOCK_LEDGER_CLOSING", "2026-09-01", "2026-09-30"));

        // then
        verify(closingGenerator).generate("store-1", FROM, TO);
        verifyNoInteractions(generatedReports);
    }

    @Test
    void aFailureIsRethrownSoTheQueueRetriesAndFinallyParksTheMessage() throws Exception {
        // given
        when(exportGenerator.generate("store-1", FROM, TO)).thenThrow(new IOException("boom"));

        // when / then
        assertThatThrownBy(() -> listener.handleMessage(EXPORT)).isInstanceOf(IOException.class);
        verifyNoInteractions(generatedReports);
    }

    @Test
    void aReportTypeWithoutAGeneratorIsAMisconfiguration() {
        // given
        ReportGenerators generators = new ReportGenerators(List.of(exportGenerator));

        // when / then
        assertThatThrownBy(() -> generators.forType(ReportType.STOCK_LEDGER_CLOSING)).isInstanceOf(IllegalStateException.class);
        assertThat(generators.forType(ReportType.STOCK_LEDGER_EXPORT)).isSameAs(exportGenerator);
    }
}
