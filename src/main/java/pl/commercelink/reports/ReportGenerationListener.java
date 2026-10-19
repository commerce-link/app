package pl.commercelink.reports;

import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDate;

@Slf4j
@Component
@RequiredArgsConstructor
class ReportGenerationListener {

    private final ReportGenerators generators;

    // Not caught: the message comes back after the visibility timeout and, after three receives, lands in the DLQ.
    // Nothing else records a failed report, so the ERROR logged here (Sentry) is the operator's only signal.
    @SqsListener(
            value = ReportGenerationPublisher.QUEUE_NAME,
            maxConcurrentMessages = "2",
            maxMessagesPerPoll = "1",
            pollTimeoutSeconds = "20"
    )
    void handleMessage(ReportGenerationRequest request) throws IOException {
        ReportType type = ReportType.valueOf(request.getReportType());
        LocalDate dateFrom = LocalDate.parse(request.getDateFrom());
        LocalDate dateTo = LocalDate.parse(request.getDateTo());
        try {
            generators.forType(type).generate(request.getStoreId(), dateFrom, dateTo);
        } catch (IOException | RuntimeException e) {
            log.error("Report {} {}..{} of store {} failed", type, dateFrom, dateTo, request.getStoreId(), e);
            throw e;
        }
    }
}
