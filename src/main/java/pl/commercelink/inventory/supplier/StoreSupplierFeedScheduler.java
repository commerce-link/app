package pl.commercelink.inventory.supplier;

import io.awspring.cloud.sqs.operations.SqsTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import pl.commercelink.scheduling.EventBridgeSchedules;
import pl.commercelink.scheduling.PollingSchedule;
import pl.commercelink.starter.util.ConversionUtil;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Component
public class StoreSupplierFeedScheduler {

    private static final String FEED_IMPORT_QUEUE = "supplier-feed-import-queue";
    private static final int CONFIGURATION_RETRY_DELAY_SECONDS = 10;

    private final String feedImportQueueArn;
    private final EventBridgeSchedules schedules;
    private final SqsTemplate sqsTemplate;

    public StoreSupplierFeedScheduler(@Value("${sqs.feed-import.queue.arn}") String feedImportQueueArn,
                                      EventBridgeSchedules schedules,
                                      SqsTemplate sqsTemplate) {
        this.feedImportQueueArn = feedImportQueueArn;
        this.schedules = schedules;
        this.sqsTemplate = sqsTemplate;
    }

    public void schedule(String storeId, String supplierName, String feedSchedule) {
        schedules.put(
                scheduleName(storeId, supplierName),
                PollingSchedule.storedOrRandomNightly(feedSchedule).awsExpression(),
                feedImportQueueArn,
                ConversionUtil.toJson(feedImportRequest(storeId, supplierName)));
    }

    public void deleteSchedule(String storeId, String supplierName) {
        schedules.delete(scheduleName(storeId, supplierName));
    }

    public Optional<String> snapshot(String storeId, String supplierName) {
        return schedules.expressionOf(scheduleName(storeId, supplierName));
    }

    public void restore(String storeId, String supplierName, Optional<String> snapshot) {
        String name = scheduleName(storeId, supplierName);
        if (snapshot.isPresent()) {
            schedules.put(name, snapshot.get(), feedImportQueueArn, ConversionUtil.toJson(feedImportRequest(storeId, supplierName)));
        } else {
            schedules.delete(name);
        }
    }

    public void triggerImmediateImport(String storeId, String supplierName) {
        if (!schedules.isEnabled()) {
            return;
        }

        sqsTemplate.send(FEED_IMPORT_QUEUE, new SqsFeedLoaderEventListener.FeedLoaderEventPayload(supplierName, storeId, 0));
    }

    public void scheduleConfigurationRetry(String storeId, String supplierName, int attempt) {
        sqsTemplate.send(to -> to.queue(FEED_IMPORT_QUEUE)
                .payload(new SqsFeedLoaderEventListener.FeedLoaderEventPayload(supplierName, storeId, attempt))
                .delaySeconds(CONFIGURATION_RETRY_DELAY_SECONDS));
    }

    private Map<String, String> feedImportRequest(String storeId, String supplierName) {
        Map<String, String> request = new LinkedHashMap<>();
        request.put("supplierName", supplierName);
        request.put("storeId", storeId);
        return request;
    }

    private String scheduleName(String storeId, String supplierName) {
        return "supplier-feed-" + storeId + "-" + supplierName.toLowerCase().replaceAll("[^a-z0-9]", "-");
    }
}
