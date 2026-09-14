package pl.commercelink.scheduling;

public class InvalidScheduleException extends RuntimeException {

    public enum Reason {
        SYNTAX,
        TOO_FREQUENT
    }

    private final Reason reason;
    private final int minIntervalMinutes;

    private InvalidScheduleException(Reason reason, String message, int minIntervalMinutes) {
        super(message);
        this.reason = reason;
        this.minIntervalMinutes = minIntervalMinutes;
    }

    static InvalidScheduleException syntax(String expression) {
        return new InvalidScheduleException(Reason.SYNTAX, "Not a valid cron expression: " + expression, 0);
    }

    static InvalidScheduleException tooFrequent(String expression, int minIntervalMinutes) {
        return new InvalidScheduleException(Reason.TOO_FREQUENT,
                "Schedule runs more often than every " + minIntervalMinutes + " minutes: " + expression,
                minIntervalMinutes);
    }

    public Reason getReason() {
        return reason;
    }

    public int getMinIntervalMinutes() {
        return minIntervalMinutes;
    }
}
