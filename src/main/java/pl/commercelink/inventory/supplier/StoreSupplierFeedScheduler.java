package pl.commercelink.inventory.supplier;

import io.awspring.cloud.sqs.operations.SqsTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import pl.commercelink.scheduling.EventBridgeSchedules;
import pl.commercelink.scheduling.PollingSchedule;
import pl.commercelink.starter.util.ConversionUtil;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.apache.commons.lang3.StringUtils.isBlank;

@Component
public class StoreSupplierFeedScheduler {

    private static final String FEED_IMPORT_QUEUE = "supplier-feed-import-queue";
    private static final int CONFIGURATION_RETRY_DELAY_SECONDS = 10;

    @Value("${application.env}")
    private String env;

    @Value("${sqs.feed-import.queue.arn}")
    private String feedImportQueueArn;

    @Autowired
    private EventBridgeSchedules schedules;

    @Autowired
    private SqsTemplate sqsTemplate;

    public void createSchedule(String storeId, String supplierName, String feedSchedule) {
        putSchedule(storeId, supplierName, feedSchedule);
    }

    public void updateSchedule(String storeId, String supplierName, String feedSchedule) {
        putSchedule(storeId, supplierName, feedSchedule);
    }

    public void deleteSchedule(String storeId, String supplierName) {
        schedules.delete(scheduleName(storeId, supplierName));
    }

    public void triggerImmediateImport(String storeId, String supplierName) {
        if (!env.equals("prod")) {
            return;
        }

        sqsTemplate.send(FEED_IMPORT_QUEUE, feedImportRequest(storeId, supplierName));
    }

    public void scheduleConfigurationRetry(String storeId, String supplierName, int attempt) {
        Map<String, String> request = feedImportRequest(storeId, supplierName);
        request.put("attempt", String.valueOf(attempt));
        sqsTemplate.send(to -> to.queue(FEED_IMPORT_QUEUE)
                .payload(request)
                .delaySeconds(CONFIGURATION_RETRY_DELAY_SECONDS));
    }

    private void putSchedule(String storeId, String supplierName, String feedSchedule) {
        schedules.put(
                scheduleName(storeId, supplierName),
                scheduleExpression(feedSchedule),
                feedImportQueueArn,
                ConversionUtil.toJson(feedImportRequest(storeId, supplierName)));
    }

    private String scheduleExpression(String feedSchedule) {
        if (isBlank(feedSchedule)) {
            return PollingSchedule.randomNightly().awsExpression();
        }
        return "cron(" + feedSchedule.trim() + ")";
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
