package pl.commercelink.scheduling;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.scheduler.SchedulerClient;
import software.amazon.awssdk.services.scheduler.model.ConflictException;
import software.amazon.awssdk.services.scheduler.model.CreateScheduleRequest;
import software.amazon.awssdk.services.scheduler.model.DeleteScheduleRequest;
import software.amazon.awssdk.services.scheduler.model.FlexibleTimeWindow;
import software.amazon.awssdk.services.scheduler.model.FlexibleTimeWindowMode;
import software.amazon.awssdk.services.scheduler.model.GetScheduleRequest;
import software.amazon.awssdk.services.scheduler.model.ResourceNotFoundException;
import software.amazon.awssdk.services.scheduler.model.Target;

import java.util.Optional;
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
                                @Nullable SchedulerClient schedulerClient) {
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

    public Optional<String> expressionOf(String name) {
        if (!isEnabled()) {
            return Optional.empty();
        }
        try {
            return Optional.of(schedulerClient.getSchedule(GetScheduleRequest.builder().name(name).build()).scheduleExpression());
        } catch (ResourceNotFoundException missing) {
            return Optional.empty();
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
