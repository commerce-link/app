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

    private final ScheduledDailyExecutionCountersRepository repository;
    private final Clock clock;

    @Autowired
    public ScheduledExecutionCounter(ScheduledDailyExecutionCountersRepository repository) {
        this(repository, Clock.system(ZoneId.of(EventBridgeSchedules.TIMEZONE)));
    }

    ScheduledExecutionCounter(ScheduledDailyExecutionCountersRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public void countCompleted(String storeId, ScheduledExecution scheduledExecution, String dimension) {
        LocalDate today = LocalDate.now(clock);
        try {
            repository.increment(storeId, today, scheduledExecution, dimension);
        } catch (RuntimeException e) {
            log.error("Execution not counted: store={} scheduledExecution={} dimension={} date={}",
                    storeId, scheduledExecution, dimension, today, e);
        }
    }
}
