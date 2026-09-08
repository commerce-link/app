package pl.commercelink.scheduling;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.scheduler.SchedulerClient;
import software.amazon.awssdk.services.scheduler.model.ConflictException;
import software.amazon.awssdk.services.scheduler.model.CreateScheduleRequest;
import software.amazon.awssdk.services.scheduler.model.DeleteScheduleRequest;
import software.amazon.awssdk.services.scheduler.model.FlexibleTimeWindow;
import software.amazon.awssdk.services.scheduler.model.FlexibleTimeWindowMode;
import software.amazon.awssdk.services.scheduler.model.ResourceNotFoundException;
import software.amazon.awssdk.services.scheduler.model.Target;
import software.amazon.awssdk.services.scheduler.model.UpdateScheduleRequest;

@Component
public class EventBridgeSchedules {

    public static final String TIMEZONE = "Europe/Warsaw";

    private final boolean enabled;
    private final String roleArn;
    private final SchedulerClient schedulerClient;

    public EventBridgeSchedules(@Value("${application.env}") String env,
                                @Value("${eventbridge.scheduler.enabled:#{null}}") Boolean enabledOverride,
                                @Value("${eventbridge.scheduler.role.arn}") String roleArn,
                                @Autowired(required = false) SchedulerClient schedulerClient) {
        this.enabled = enabledOverride != null ? enabledOverride : "prod".equals(env);
        this.roleArn = roleArn;
        this.schedulerClient = schedulerClient;
    }

    public boolean isEnabled() {
        return enabled && schedulerClient != null;
    }

    public void put(String name, String scheduleExpression, String targetArn, String input) {
        if (!isEnabled()) {
            return;
        }
        Target target = Target.builder()
                .arn(targetArn)
                .roleArn(roleArn)
                .input(input)
                .build();
        FlexibleTimeWindow window = FlexibleTimeWindow.builder()
                .mode(FlexibleTimeWindowMode.OFF)
                .build();
        try {
            schedulerClient.createSchedule(CreateScheduleRequest.builder()
                    .name(name)
                    .scheduleExpression(scheduleExpression)
                    .scheduleExpressionTimezone(TIMEZONE)
                    .flexibleTimeWindow(window)
                    .target(target)
                    .build());
        } catch (ConflictException existing) {
            schedulerClient.updateSchedule(UpdateScheduleRequest.builder()
                    .name(name)
                    .scheduleExpression(scheduleExpression)
                    .scheduleExpressionTimezone(TIMEZONE)
                    .flexibleTimeWindow(window)
                    .target(target)
                    .build());
        }
    }

    public void delete(String name) {
        if (!isEnabled()) {
            return;
        }
        try {
            schedulerClient.deleteSchedule(DeleteScheduleRequest.builder().name(name).build());
        } catch (ResourceNotFoundException ignored) {
        }
    }
}
