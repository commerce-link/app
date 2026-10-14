package pl.commercelink.reports;

import io.awspring.cloud.sqs.operations.SqsTemplate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/** Orders a report: the message is all there is, the worker does the rest and the result shows up on its page. */
@Component
@RequiredArgsConstructor
public class ReportGenerationPublisher {

    static final String QUEUE_NAME = "report-generation-queue";

    private final SqsTemplate sqsTemplate;

    public void order(String storeId, ReportType type, LocalDate dateFrom, LocalDate dateTo) {
        sqsTemplate.send(to -> to
                .queue(QUEUE_NAME)
                .payload(new ReportGenerationRequest(storeId, type.name(), dateFrom.toString(), dateTo.toString())));
    }
}
