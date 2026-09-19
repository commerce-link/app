package pl.commercelink.scheduling;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

@Component
@Slf4j
public class ScheduledExecutionCounter {

    private final DailyScheduleExecutionCountRepository repository;
    private final Clock clock;

    @Autowired
    public ScheduledExecutionCounter(DailyScheduleExecutionCountRepository repository) {
        this(repository, Clock.system(ZoneId.of(EventBridgeSchedules.TIMEZONE)));
    }

    ScheduledExecutionCounter(DailyScheduleExecutionCountRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public void countCompleted(String storeId, ScheduledExecution type, String target) {
        LocalDate today = LocalDate.now(clock);
        try {
            repository.increment(storeId, today, type, target);
        } catch (RuntimeException e) {
            log.error("Execution not counted: store={} type={} target={} date={}", storeId, type, target, today, e);
        }
    }
}
