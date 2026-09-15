package pl.commercelink.scheduling;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.apache.commons.lang3.StringUtils.isBlank;

/**
 * Human-readable summary of a stored feed schedule, as a message code plus its arguments so the
 * caller resolves it in the operator's own language.
 * <p>
 * It recognises exactly the shapes the schedule builder produces. Anything else -- a hand-written
 * advanced expression -- is reported as CUSTOM rather than paraphrased, because a wrong summary of
 * when a feed is pulled is worse than no summary at all; the expression itself is shown alongside.
 */
public record PollingScheduleDescription(String code, List<Object> args) {

    private static final String PREFIX = "store.supplier.schedule.summary.";
    static final String DEFAULT = PREFIX + "default";
    static final String CUSTOM = PREFIX + "custom";
    static final String EVERY_MINUTES = PREFIX + "every.minutes";
    static final String EVERY_HOURS = PREFIX + "every.hours";
    static final String EVERY_DAYS = PREFIX + "every.days";
    static final String AT = PREFIX + "at";

    // Beyond this the table cell would be a wall of times; the expression stays readable in the
    // tooltip next to it, so falling back to CUSTOM loses nothing.
    private static final int MAX_LISTED_TIMES = 6;

    private static final Pattern STEP_FROM_ZERO = Pattern.compile("^0/(\\d{1,2})$");
    private static final Pattern STEP_FROM_ONE = Pattern.compile("^1/(\\d{1,2})$");
    private static final Pattern NUMBER_LIST = Pattern.compile("^\\d{1,2}(,\\d{1,2})*$");
    private static final List<String> DAYS_OF_WEEK =
            List.of("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN", "MON-FRI");

    public static PollingScheduleDescription of(String expression) {
        if (isBlank(expression)) {
            return new PollingScheduleDescription(DEFAULT, List.of());
        }
        String[] fields = PollingSchedule.normalizeOrNull(expression).split(" ");
        if (fields.length != 6 || !"*".equals(fields[3]) || !"*".equals(fields[5])) {
            return custom();
        }
        String minutes = fields[0];
        String hours = fields[1];
        String dayOfMonth = fields[2];
        String dayOfWeek = fields[4];

        if ("*".equals(dayOfMonth) && "?".equals(dayOfWeek)) {
            Matcher everyMinutes = STEP_FROM_ZERO.matcher(minutes);
            if (everyMinutes.matches() && "*".equals(hours)) {
                return new PollingScheduleDescription(EVERY_MINUTES, List.of(Integer.valueOf(everyMinutes.group(1))));
            }
            if ("0".equals(minutes) && "*".equals(hours)) {
                return new PollingScheduleDescription(EVERY_HOURS, List.of(1));
            }
            Matcher everyHours = STEP_FROM_ZERO.matcher(hours);
            if ("0".equals(minutes) && everyHours.matches()) {
                return new PollingScheduleDescription(EVERY_HOURS, List.of(Integer.valueOf(everyHours.group(1))));
            }
        }
        Matcher everyDays = STEP_FROM_ONE.matcher(dayOfMonth);
        if ("0".equals(minutes) && "0".equals(hours) && everyDays.matches() && "?".equals(dayOfWeek)) {
            return new PollingScheduleDescription(EVERY_DAYS, List.of(Integer.valueOf(everyDays.group(1))));
        }
        return atTimes(minutes, hours, dayOfMonth, dayOfWeek);
    }

    /**
     * @return the message arguments in the shape Thymeleaf's #messages.msgWithParams expects.
     */
    public Object[] messageArgs() {
        return args.toArray();
    }

    private static PollingScheduleDescription atTimes(String minutes, String hours,
                                                      String dayOfMonth, String dayOfWeek) {
        if (!NUMBER_LIST.matcher(minutes).matches() || !NUMBER_LIST.matcher(hours).matches()) {
            return custom();
        }
        String code;
        if ("*".equals(dayOfMonth) && "?".equals(dayOfWeek)) {
            code = AT;
        } else if ("?".equals(dayOfMonth) && DAYS_OF_WEEK.contains(dayOfWeek)) {
            code = AT + "." + dayOfWeek;
        } else {
            return custom();
        }
        TreeSet<Integer> hourValues = values(hours, 24);
        TreeSet<Integer> minuteValues = values(minutes, 60);
        if (hourValues == null || minuteValues == null
                || hourValues.size() * minuteValues.size() > MAX_LISTED_TIMES) {
            return custom();
        }
        List<String> times = new ArrayList<>();
        for (int hour : hourValues) {
            for (int minute : minuteValues) {
                times.add(String.format(Locale.ROOT, "%02d:%02d", hour, minute));
            }
        }
        return new PollingScheduleDescription(code, List.of(String.join(", ", times)));
    }

    private static TreeSet<Integer> values(String field, int limit) {
        TreeSet<Integer> values = new TreeSet<>();
        for (String item : field.split(",")) {
            int value = Integer.parseInt(item);
            if (value >= limit) {
                return null;
            }
            values.add(value);
        }
        return values;
    }

    private static PollingScheduleDescription custom() {
        return new PollingScheduleDescription(CUSTOM, List.of());
    }
}
