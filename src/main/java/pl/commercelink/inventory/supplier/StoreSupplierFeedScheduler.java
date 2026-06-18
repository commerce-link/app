package pl.commercelink.inventory.supplier;

import io.awspring.cloud.sqs.operations.SqsTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import pl.commercelink.starter.util.ConversionUtil;
import software.amazon.awssdk.services.scheduler.SchedulerClient;
import software.amazon.awssdk.services.scheduler.model.CreateScheduleRequest;
import software.amazon.awssdk.services.scheduler.model.DeleteScheduleRequest;
import software.amazon.awssdk.services.scheduler.model.FlexibleTimeWindow;
import software.amazon.awssdk.services.scheduler.model.FlexibleTimeWindowMode;
import software.amazon.awssdk.services.scheduler.model.ResourceNotFoundException;
import software.amazon.awssdk.services.scheduler.model.Target;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

@Component
public class StoreSupplierFeedScheduler {

    private static final String FEED_IMPORT_QUEUE = "supplier-feed-import-queue";

    @Value("${application.env}")
    private String env;

    @Value("${sqs.feed-import.queue.arn}")
    private String feedImportQueueArn;

    @Value("${eventbridge.scheduler.role.arn}")
    private String eventBridgeSchedulerRoleArn;

    @Autowired(required = false)
    private SchedulerClient schedulerClient;

    @Autowired
    private SqsTemplate sqsTemplate;

    public void createSchedule(String storeId, String supplierName) {
        if (!env.equals("prod")) {
            return;
        }

        CreateScheduleRequest request = CreateScheduleRequest.builder()
                .name(scheduleName(storeId, supplierName))
                .scheduleExpression("cron(" + generateRandomDailySchedule() + ")")
                .scheduleExpressionTimezone("Europe/Warsaw")
                .flexibleTimeWindow(FlexibleTimeWindow.builder()
                        .mode(FlexibleTimeWindowMode.OFF)
                        .build())
                .target(Target.builder()
                        .arn(feedImportQueueArn)
                        .roleArn(eventBridgeSchedulerRoleArn)
                        .input(ConversionUtil.toJson(feedImportRequest(storeId, supplierName)))
                        .build())
                .build();

        schedulerClient.createSchedule(request);
    }

    public void deleteSchedule(String storeId, String supplierName) {
        if (!env.equals("prod")) {
            return;
        }

        try {
            schedulerClient.deleteSchedule(DeleteScheduleRequest.builder()
                    .name(scheduleName(storeId, supplierName))
                    .build());
        } catch (ResourceNotFoundException ignored) {
        }
    }

    public void triggerImmediateImport(String storeId, String supplierName) {
        if (!env.equals("prod")) {
            return;
        }

        sqsTemplate.send(FEED_IMPORT_QUEUE, feedImportRequest(storeId, supplierName));
    }

    private Map<String, String> feedImportRequest(String storeId, String supplierName) {
        Map<String, String> request = new LinkedHashMap<>();
        request.put("supplierName", supplierName);
        request.put("storeId", storeId);
        return request;
    }

    private String generateRandomDailySchedule() {
        Random random = new Random();
        int[] allowedHours = { 23, 0, 1, 2, 3, 4 };
        int hour = allowedHours[random.nextInt(allowedHours.length)];
        int minute = random.nextInt(60);
        return String.format("%d %d * * ? *", minute, hour);
    }

    private String scheduleName(String storeId, String supplierName) {
        return "supplier-feed-" + storeId + "-" + supplierName.toLowerCase().replaceAll("[^a-z0-9]", "-");
    }
}
