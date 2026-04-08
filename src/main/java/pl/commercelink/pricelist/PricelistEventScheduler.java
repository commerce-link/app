package pl.commercelink.pricelist;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import pl.commercelink.starter.util.ConversionUtil;
import software.amazon.awssdk.services.scheduler.SchedulerClient;
import software.amazon.awssdk.services.scheduler.model.*;

import java.util.Random;

@Component
public class PricelistEventScheduler {

    @Value("${application.env}")
    private String env;

    @Value("${sqs.pricelist.queue.arn}")
    private String pricelistQueueArn;

    @Value("${eventbridge.scheduler.role.arn}")
    private String eventBridgeSchedulerRoleArn;

    @Autowired(required = false)
    private SchedulerClient schedulerClient;

    public void createRecurringSchedule(String storeId, String catalogId) {
        if (!env.equals("prod")) {
            return;
        }

        String scheduleBody = ConversionUtil.toJson(new PricelistEventPayload(storeId, catalogId));
        String cronExpression = generateRandomSchedule();

        CreateScheduleRequest request = CreateScheduleRequest.builder()
                .name(scheduleName(storeId, catalogId))
                .scheduleExpression("cron(" + cronExpression + ")")
                .scheduleExpressionTimezone("Europe/Warsaw")
                .flexibleTimeWindow(FlexibleTimeWindow.builder()
                        .mode(FlexibleTimeWindowMode.OFF)
                        .build())
                .target(Target.builder()
                        .arn(pricelistQueueArn)
                        .roleArn(eventBridgeSchedulerRoleArn)
                        .input(scheduleBody)
                        .build())
                .build();

        schedulerClient.createSchedule(request);
    }

    public void deleteSchedule(String storeId, String catalogId) {
        if (!env.equals("prod")) {
            return;
        }

        DeleteScheduleRequest deleteRequest = DeleteScheduleRequest.builder()
                .name(scheduleName(storeId, catalogId))
                .build();

        try {
            schedulerClient.deleteSchedule(deleteRequest);
        } catch (ResourceNotFoundException e) {
            throw new RuntimeException("No existing schedule to delete: " + scheduleName(storeId, catalogId), e);
        }
    }

    // Random time between 23PM - 5AM
    private String generateRandomSchedule() {
        Random random = new Random();
        int[] allowedHours = { 23, 0, 1, 2, 3, 4 };
        int hour = allowedHours[random.nextInt(allowedHours.length)];
        int minute = random.nextInt(60);
        return String.format("%d %d * * ? *", minute, hour);
    }

    private String scheduleName(String storeId, String catalogId) {
        return "pricelist-" + storeId + "-" + catalogId;
    }

}
