package pl.commercelink.reports;

import io.awspring.cloud.sqs.operations.SqsSendOptions;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ReportGenerationPublisherTest {

    @Mock
    private SqsTemplate sqsTemplate;
    @Mock
    private SqsSendOptions<Object> options;
    @InjectMocks
    private ReportGenerationPublisher publisher;

    @Test
    @SuppressWarnings("unchecked")
    void theMessageCarriesTheStoreTheReportTypeAndTheRange() {
        // given
        ArgumentCaptor<Consumer<SqsSendOptions<Object>>> captor = ArgumentCaptor.forClass(Consumer.class);
        org.mockito.Mockito.when(options.queue(any())).thenReturn(options);

        // when
        publisher.order("store-1", ReportType.STOCK_LEDGER_CLOSING, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));

        // then
        verify(sqsTemplate).send(captor.capture());
        captor.getValue().accept(options);
        verify(options).queue("report-generation-queue");
        verify(options).payload(new ReportGenerationRequest("store-1", "STOCK_LEDGER_CLOSING", "2026-09-01", "2026-09-30"));
        assertThat(ReportGenerationPublisher.QUEUE_NAME).isEqualTo("report-generation-queue");
    }
}
