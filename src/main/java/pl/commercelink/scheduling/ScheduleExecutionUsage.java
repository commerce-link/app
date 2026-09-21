package pl.commercelink.scheduling;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Function;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.summingLong;

public record ScheduleExecutionUsage(List<DailyScheduleExecutionCount> counts) {

    public ScheduleExecutionUsage {
        counts = List.copyOf(counts);
    }

    public long total(ScheduledExecution type) {
        return counts.stream()
                .filter(count -> count.getExecutionType() == type)
                .mapToLong(DailyScheduleExecutionCount::getExecutionCount)
                .sum();
    }

    public Map<ScheduledExecution, Long> totalsByType() {
        return counts.stream().collect(groupingBy(DailyScheduleExecutionCount::getExecutionType,
                () -> new EnumMap<>(ScheduledExecution.class),
                summingLong(DailyScheduleExecutionCount::getExecutionCount)));
    }

    public SortedMap<String, Long> totalsByTarget(ScheduledExecution type) {
        return totalsOf(type, DailyScheduleExecutionCount::getTarget);
    }

    public SortedMap<LocalDate, Long> totalsByDay(ScheduledExecution type) {
        return totalsOf(type, DailyScheduleExecutionCount::getExecutionDate);
    }

    private <K extends Comparable<? super K>> SortedMap<K, Long> totalsOf(ScheduledExecution type, Function<DailyScheduleExecutionCount, K> key) {
        return counts.stream()
                .filter(count -> count.getExecutionType() == type)
                .collect(groupingBy(key, TreeMap::new, summingLong(DailyScheduleExecutionCount::getExecutionCount)));
    }
}
