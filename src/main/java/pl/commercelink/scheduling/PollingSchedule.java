package pl.commercelink.scheduling;

import java.util.Locale;
import java.util.Random;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.apache.commons.lang3.StringUtils.isBlank;

public final class PollingSchedule {

    private static final int MINUTES_PER_HOUR = 60;
    private static final int HOURS_PER_DAY = 24;
    private static final int[] NIGHT_HOURS = { 23, 0, 1, 2, 3, 4 };

    private static final String NUMBER = "\\d{1,4}";
    private static final String NUMERIC_ITEM = "(\\*|" + NUMBER + "(-" + NUMBER + ")?)(/" + NUMBER + ")?";
    private static final Pattern HOURS = numericList(NUMERIC_ITEM);
    private static final Pattern YEAR = numericList(NUMERIC_ITEM);
    private static final Pattern DAY_OF_MONTH = numericList("\\?|L|LW|" + NUMBER + "W|" + NUMERIC_ITEM);
    private static final String MONTH_NAME = "JAN|FEB|MAR|APR|MAY|JUN|JUL|AUG|SEP|OCT|NOV|DEC";
    private static final Pattern MONTH = numericList(
            "(\\*|(" + NUMBER + "|" + MONTH_NAME + ")(-(" + NUMBER + "|" + MONTH_NAME + "))?)(/" + NUMBER + ")?");
    private static final String DAY_NAME = "SUN|MON|TUE|WED|THU|FRI|SAT";
    private static final Pattern DAY_OF_WEEK = numericList(
            "\\?|L|\\dL|\\d#\\d|(\\*|(\\d|" + DAY_NAME + ")(-(\\d|" + DAY_NAME + "))?)(/" + NUMBER + ")?");

    private static final Pattern STEP = Pattern.compile("/(\\d+)");
    private static final Pattern NUMBER_TOKEN = Pattern.compile("\\d+");

    private final String expression;

    private PollingSchedule(String expression) {
        this.expression = expression;
    }

    public static PollingSchedule parse(String expression, int minIntervalMinutes) {
        if (isBlank(expression)) {
            throw InvalidScheduleException.syntax(expression);
        }
        String normalized = normalizeOrNull(expression);
        String[] fields = normalized.split(" ");
        if (fields.length != 6) {
            throw InvalidScheduleException.syntax(expression);
        }
        TreeSet<Integer> minutes = expand(fields[0], MINUTES_PER_HOUR, expression);
        requireMatch(HOURS, fields[1], expression);
        requireRange(fields[1], 0, 23, expression);
        requireMatch(DAY_OF_MONTH, fields[2], expression);
        requireRange(fields[2], 1, 31, expression);
        requireMatch(MONTH, fields[3], expression);
        requireRange(fields[3], 1, 12, expression);
        requireMatch(DAY_OF_WEEK, fields[4], expression);
        requireRange(fields[4], 1, 7, expression);
        requireMatch(YEAR, fields[5], expression);
        requireRange(fields[5], 1970, 2199, expression);
        boolean exactlyOneDayFieldUnspecified = "?".equals(fields[2]) != "?".equals(fields[4]);
        if (!exactlyOneDayFieldUnspecified) {
            throw InvalidScheduleException.syntax(expression);
        }
        TreeSet<Integer> hours = expand(fields[1], HOURS_PER_DAY, expression);
        requireInterval(minutes, hours, expression, minIntervalMinutes);
        return new PollingSchedule(normalized);
    }

    public static PollingSchedule randomNightly() {
        Random random = new Random();
        int hour = NIGHT_HOURS[random.nextInt(NIGHT_HOURS.length)];
        int minute = random.nextInt(MINUTES_PER_HOUR);
        return new PollingSchedule(String.format("%d %d * * ? *", minute, hour));
    }

    public static PollingSchedule storedOrRandomNightly(String stored) {
        String normalized = normalizeOrNull(stored);
        return normalized == null ? randomNightly() : new PollingSchedule(normalized);
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

    private static Pattern numericList(String item) {
        return Pattern.compile("^(" + item + ")(,(" + item + "))*$");
    }

    private static void requireMatch(Pattern pattern, String field, String expression) {
        if (!pattern.matcher(field).matches()) {
            throw InvalidScheduleException.syntax(expression);
        }
    }

    private static void requireRange(String field, int min, int max, String expression) {
        Matcher steps = STEP.matcher(field);
        while (steps.find()) {
            if (Integer.parseInt(steps.group(1)) < 1) {
                throw InvalidScheduleException.syntax(expression);
            }
        }
        Matcher numbers = NUMBER_TOKEN.matcher(STEP.matcher(field).replaceAll(""));
        while (numbers.find()) {
            int value = Integer.parseInt(numbers.group());
            if (value < min || value > max) {
                throw InvalidScheduleException.syntax(expression);
            }
        }
    }

    private static TreeSet<Integer> expand(String field, int limit, String expression) {
        TreeSet<Integer> values = new TreeSet<>();
        for (String item : field.split(",")) {
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
