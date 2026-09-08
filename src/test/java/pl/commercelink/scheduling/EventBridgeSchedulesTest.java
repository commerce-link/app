package pl.commercelink.scheduling;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.scheduler.SchedulerClient;
import software.amazon.awssdk.services.scheduler.model.ConflictException;
import software.amazon.awssdk.services.scheduler.model.CreateScheduleRequest;
import software.amazon.awssdk.services.scheduler.model.DeleteScheduleRequest;
import software.amazon.awssdk.services.scheduler.model.FlexibleTimeWindowMode;
import software.amazon.awssdk.services.scheduler.model.ResourceNotFoundException;
import software.amazon.awssdk.services.scheduler.model.UpdateScheduleRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventBridgeSchedulesTest {

    private static final String ROLE_ARN = "arn:aws:iam::1:role/scheduler";
    private static final String TARGET_ARN = "arn:aws:sqs:eu-central-1:1:queue";

    @Mock
    private SchedulerClient schedulerClient;

    @Test
    void isEnabledOnProdWithoutOverride() {
        // when
        EventBridgeSchedules schedules = new EventBridgeSchedules("prod", null, ROLE_ARN, schedulerClient);

        // then
        assertThat(schedules.isEnabled()).isTrue();
    }

    @Test
    void isDisabledOutsideProdWithoutOverride() {
        // when
        EventBridgeSchedules schedules = new EventBridgeSchedules("localhost", null, ROLE_ARN, schedulerClient);

        // then
        assertThat(schedules.isEnabled()).isFalse();
    }

    @Test
    void overrideWinsOverEnvironment() {
        // when / then
        assertThat(new EventBridgeSchedules("localhost", true, ROLE_ARN, schedulerClient).isEnabled()).isTrue();
        assertThat(new EventBridgeSchedules("prod", false, ROLE_ARN, schedulerClient).isEnabled()).isFalse();
    }

    @Test
    void isDisabledWithoutSchedulerClient() {
        // when
        EventBridgeSchedules schedules = new EventBridgeSchedules("prod", null, ROLE_ARN, null);

        // then
        assertThat(schedules.isEnabled()).isFalse();
    }

    @Test
    void putDoesNothingWhenDisabled() {
        // given
        EventBridgeSchedules schedules = new EventBridgeSchedules("localhost", null, ROLE_ARN, schedulerClient);

        // when
        schedules.put("name", "cron(0 5 * * ? *)", TARGET_ARN, "{}");
        schedules.delete("name");

        // then
        verifyNoInteractions(schedulerClient);
    }

    @Test
    void putCreatesScheduleWithTargetAndTimezone() {
        // given
        EventBridgeSchedules schedules = new EventBridgeSchedules("prod", null, ROLE_ARN, schedulerClient);

        // when
        schedules.put("supplier-feed-s1-acme", "cron(0/30 9-17 * * ? *)", TARGET_ARN, "{\"storeId\":\"s1\"}");

        // then
        ArgumentCaptor<CreateScheduleRequest> request = ArgumentCaptor.forClass(CreateScheduleRequest.class);
        verify(schedulerClient).createSchedule(request.capture());
        assertThat(request.getValue().name()).isEqualTo("supplier-feed-s1-acme");
        assertThat(request.getValue().scheduleExpression()).isEqualTo("cron(0/30 9-17 * * ? *)");
        assertThat(request.getValue().scheduleExpressionTimezone()).isEqualTo("Europe/Warsaw");
        assertThat(request.getValue().flexibleTimeWindow().mode()).isEqualTo(FlexibleTimeWindowMode.OFF);
        assertThat(request.getValue().target().arn()).isEqualTo(TARGET_ARN);
        assertThat(request.getValue().target().roleArn()).isEqualTo(ROLE_ARN);
        assertThat(request.getValue().target().input()).isEqualTo("{\"storeId\":\"s1\"}");
        verify(schedulerClient, never()).updateSchedule(any(UpdateScheduleRequest.class));
    }

    @Test
    void putUpdatesExistingScheduleOnConflict() {
        // given
        EventBridgeSchedules schedules = new EventBridgeSchedules("prod", null, ROLE_ARN, schedulerClient);
        when(schedulerClient.createSchedule(any(CreateScheduleRequest.class)))
                .thenThrow(ConflictException.builder().message("exists").build());

        // when
        schedules.put("supplier-feed-s1-acme", "cron(0 5 * * ? *)", TARGET_ARN, "{}");

        // then
        ArgumentCaptor<UpdateScheduleRequest> request = ArgumentCaptor.forClass(UpdateScheduleRequest.class);
        verify(schedulerClient).updateSchedule(request.capture());
        assertThat(request.getValue().name()).isEqualTo("supplier-feed-s1-acme");
        assertThat(request.getValue().scheduleExpression()).isEqualTo("cron(0 5 * * ? *)");
        assertThat(request.getValue().scheduleExpressionTimezone()).isEqualTo("Europe/Warsaw");
        assertThat(request.getValue().target().arn()).isEqualTo(TARGET_ARN);
        assertThat(request.getValue().target().roleArn()).isEqualTo(ROLE_ARN);
    }

    @Test
    void deleteIgnoresMissingSchedule() {
        // given
        EventBridgeSchedules schedules = new EventBridgeSchedules("prod", null, ROLE_ARN, schedulerClient);
        when(schedulerClient.deleteSchedule(any(DeleteScheduleRequest.class)))
                .thenThrow(ResourceNotFoundException.builder().message("missing").build());

        // when
        schedules.delete("supplier-feed-s1-acme");

        // then
        ArgumentCaptor<DeleteScheduleRequest> request = ArgumentCaptor.forClass(DeleteScheduleRequest.class);
        verify(schedulerClient).deleteSchedule(request.capture());
        assertThat(request.getValue().name()).isEqualTo("supplier-feed-s1-acme");
    }
}
