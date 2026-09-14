package pl.commercelink.scheduling;

import java.util.Locale;
import java.util.Random;
import java.util.TreeSet;
import java.util.regex.Pattern;

import static org.apache.commons.lang3.StringUtils.isBlank;

public final class PollingSchedule {

    private static final int MINUTES_PER_HOUR = 60;
    private static final int HOURS_PER_DAY = 24;
    private static final int[] NIGHT_HOURS = { 23, 0, 1, 2, 3, 4 };

    private static final String STEP = "(/\\d{1,4})?";
    private static final String DAY_OF_MONTH_NUMBER = "([1-9]|[12]\\d|3[01])";
    private static final String MONTH_VALUE = "([1-9]|1[0-2]|JAN|FEB|MAR|APR|MAY|JUN|JUL|AUG|SEP|OCT|NOV|DEC)";
    private static final String DAY_OF_WEEK_VALUE = "([1-7]|SUN|MON|TUE|WED|THU|FRI|SAT)";
    private static final String YEAR_NUMBER = "(19[7-9]\\d|2[01]\\d\\d)";

    private static final Pattern DAY_OF_MONTH = Pattern.compile(
            "^(\\?|L|LW|" + DAY_OF_MONTH_NUMBER + "W|" + list(DAY_OF_MONTH_NUMBER) + ")$");
    private static final Pattern MONTH = Pattern.compile("^" + list(MONTH_VALUE) + "$");
    private static final Pattern DAY_OF_WEEK = Pattern.compile(
            "^(\\?|L|[1-7]L|[1-7]#[1-5]|" + list(DAY_OF_WEEK_VALUE) + ")$");
    private static final Pattern YEAR = Pattern.compile("^" + list(YEAR_NUMBER) + "$");

    private final String expression;

    private PollingSchedule(String expression) {
        this.expression = expression;
    }

    public static PollingSchedule parse(String expression, int minIntervalMinutes) {
        if (isBlank(expression)) {
            throw InvalidScheduleException.syntax(expression);
        }
        String[] fields = expression.split(" ");
        if (fields.length != 6) {
            throw InvalidScheduleException.syntax(expression);
        }
        TreeSet<Integer> minutes = expand(fields[0], MINUTES_PER_HOUR, expression);
        TreeSet<Integer> hours = expand(fields[1], HOURS_PER_DAY, expression);
        requireMatch(DAY_OF_MONTH, fields[2], expression);
        requireMatch(MONTH, fields[3], expression);
        requireMatch(DAY_OF_WEEK, fields[4], expression);
        requireMatch(YEAR, fields[5], expression);
        boolean exactlyOneDayFieldUnspecified = "?".equals(fields[2]) != "?".equals(fields[4]);
        if (!exactlyOneDayFieldUnspecified) {
            throw InvalidScheduleException.syntax(expression);
        }
        requireInterval(minutes, hours, expression, minIntervalMinutes);
        return new PollingSchedule(expression);
    }

    public static PollingSchedule randomNightly() {
        Random random = new Random();
        int hour = NIGHT_HOURS[random.nextInt(NIGHT_HOURS.length)];
        int minute = random.nextInt(MINUTES_PER_HOUR);
        return new PollingSchedule(String.format("%d %d * * ? *", minute, hour));
    }

    public static PollingSchedule storedOrRandomNightly(String stored) {
        return isBlank(stored) ? randomNightly() : new PollingSchedule(stored);
    }

    public static String normalizeOrNull(String expression) {
        return isBlank(expression) ? null : expression.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }

    public String expression() {
        return expression;
    }

    public String awsExpression() {
        return "cron(" + expression + ")";
    }

    private static String list(String value) {
        String item = "(\\*|" + value + "(-" + value + ")?)" + STEP;
        return item + "(," + item + ")*";
    }

    private static void requireMatch(Pattern pattern, String field, String expression) {
        if (!pattern.matcher(field).matches()) {
            throw InvalidScheduleException.syntax(expression);
        }
    }

    private static TreeSet<Integer> expand(String field, int limit, String expression) {
        TreeSet<Integer> values = new TreeSet<>();
        for (String item : field.split(",", -1)) {
            String[] stepParts = item.split("/", -1);
            if (stepParts.length > 2) {
                throw InvalidScheduleException.syntax(expression);
            }
            int step = stepParts.length == 2 ? parseValue(stepParts[1], limit, expression) : 1;
            if (step < 1) {
                throw InvalidScheduleException.syntax(expression);
            }
            int from;
            int to;
            if ("*".equals(stepParts[0])) {
                from = 0;
                to = limit - 1;
            } else {
                String[] range = stepParts[0].split("-", -1);
                if (range.length > 2) {
                    throw InvalidScheduleException.syntax(expression);
                }
                from = parseValue(range[0], limit, expression);
                to = range.length == 2 ? parseValue(range[1], limit, expression)
                        : (stepParts.length == 2 ? limit - 1 : from);
                if (from > to) {
                    throw InvalidScheduleException.syntax(expression);
                }
            }
            for (int value = from; value <= to; value += step) {
                values.add(value);
            }
        }
        return values;
    }

    private static int parseValue(String value, int limit, String expression) {
        if (!value.matches("\\d{1,2}")) {
            throw InvalidScheduleException.syntax(expression);
        }
        int parsed = Integer.parseInt(value);
        if (parsed >= limit) {
            throw InvalidScheduleException.syntax(expression);
        }
        return parsed;
    }

    private static void requireInterval(TreeSet<Integer> minutes, TreeSet<Integer> hours, String expression, int minIntervalMinutes) {
        if (minIntervalMinutes <= 1) {
            return;
        }
        int gap = MINUTES_PER_HOUR;
        Integer previous = null;
        for (int minute : minutes) {
            if (previous != null) {
                gap = Math.min(gap, minute - previous);
            }
            previous = minute;
        }
        if (hasAdjacentHours(hours)) {
            gap = Math.min(gap, minutes.first() + MINUTES_PER_HOUR - minutes.last());
        }
        if (gap < minIntervalMinutes) {
            throw InvalidScheduleException.tooFrequent(expression, minIntervalMinutes);
        }
    }

    private static boolean hasAdjacentHours(TreeSet<Integer> hours) {
        for (int hour : hours) {
            if (hours.contains((hour + 1) % HOURS_PER_DAY)) {
                return true;
            }
        }
        return false;
    }
}
