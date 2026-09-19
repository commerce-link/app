package pl.commercelink.scheduling;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;

@Service
@RequiredArgsConstructor
public class ScheduleExecutionUsageService {

    private final DailyScheduleExecutionCountRepository repository;

    public ScheduleExecutionUsage monthlyUsage(String storeId, YearMonth month) {
        return new ScheduleExecutionUsage(repository.findMonth(storeId, month));
    }

    public ScheduleExecutionUsage dailyUsage(String storeId, LocalDate date) {
        return new ScheduleExecutionUsage(repository.findDay(storeId, date));
    }
}
