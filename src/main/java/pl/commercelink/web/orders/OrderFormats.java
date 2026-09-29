package pl.commercelink.web.orders;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/** Formats order dates the way the order screens show them, so every screen agrees on one date and time layout. */
public final class OrderFormats {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm");

    private OrderFormats() {
    }

    public static String date(LocalDate date) {
        return date == null ? null : DATE.format(date);
    }

    public static String date(LocalDateTime dateTime) {
        return dateTime == null ? null : DATE.format(dateTime);
    }

    public static String dateTime(LocalDateTime dateTime) {
        return dateTime == null ? null : DATE_TIME.format(dateTime);
    }

    /**
     * A moment that may have been entered as a date alone: midnight shows the date only (the shipment form saves a
     * new date at the start of the day, so "00:00" would be a time nobody entered); any other time shows with it.
     */
    public static String moment(LocalDateTime dateTime) {
        return dateTime == null ? null
                : dateTime.toLocalTime().equals(LocalTime.MIDNIGHT) ? DATE.format(dateTime) : DATE_TIME.format(dateTime);
    }

    // The ISO form is the value an <input type="date"> needs, not display text.
    public static String isoDate(LocalDate date) {
        return date == null ? null : date.toString();
    }
}
