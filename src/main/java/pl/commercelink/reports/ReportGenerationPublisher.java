package pl.commercelink.reports;

import io.awspring.cloud.sqs.operations.SqsTemplate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Orders a report: the message is all there is, the worker does the rest and the result shows up on its page. One
 * message group per store keeps a store's reports in the order they were ordered, which the closings need (a period
 * can only follow the last closed one); other stores go on in parallel. The deduplication id is unique per message, so
 * ordering a report again (to regenerate it) is never swallowed as a duplicate.
 */
@Component
@RequiredArgsConstructor
public class ReportGenerationPublisher {

    static final String QUEUE_NAME = "report-generation-queue.fifo";

    private final SqsTemplate sqsTemplate;

    public void order(String storeId, ReportType type, LocalDate dateFrom, LocalDate dateTo) {
        sqsTemplate.send(to -> to
                .queue(QUEUE_NAME)
                .payload(new ReportGenerationRequest(storeId, type.name(), dateFrom.toString(), dateTo.toString()))
                .messageGroupId(storeId)
                .messageDeduplicationId(UUID.randomUUID().toString()));
    }
}
