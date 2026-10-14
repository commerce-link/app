package pl.commercelink.reports;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportGenerationListenerTest {

    private static final LocalDate FROM = LocalDate.of(2026, 9, 1);
    private static final LocalDate TO = LocalDate.of(2026, 9, 30);
    private static final ReportGenerationRequest CLOSING =
            new ReportGenerationRequest("store-1", "STOCK_LEDGER_CLOSING", "2026-09-01", "2026-09-30");

    @Mock
    private ReportGenerator closingGenerator;

    private ReportGenerationListener listener;

    @BeforeEach
    void setUp() {
        when(closingGenerator.type()).thenReturn(ReportType.STOCK_LEDGER_CLOSING);
        listener = new ReportGenerationListener(new ReportGenerators(List.of(closingGenerator)));
    }

    @Test
    void theMessageIsHandedToTheGeneratorOfItsReportType() throws Exception {
        // given

        // when
        listener.handleMessage(CLOSING);

        // then
        verify(closingGenerator).generate("store-1", FROM, TO);
    }

    @Test
    void aFailureIsRethrownSoTheQueueRetriesAndFinallyParksTheMessage() throws Exception {
        // given
        doThrow(new IOException("boom")).when(closingGenerator).generate("store-1", FROM, TO);

        // when / then
        assertThatThrownBy(() -> listener.handleMessage(CLOSING)).isInstanceOf(IOException.class);
    }

    @Test
    void aReportTypeWithoutAGeneratorIsAMisconfiguration() {
        // given
        ReportGenerators generators = new ReportGenerators(List.of());

        // when / then
        assertThatThrownBy(() -> generators.forType(ReportType.STOCK_LEDGER_CLOSING)).isInstanceOf(IllegalStateException.class);
        assertThat(new ReportGenerators(List.of(closingGenerator)).forType(ReportType.STOCK_LEDGER_CLOSING)).isSameAs(closingGenerator);
    }
}
