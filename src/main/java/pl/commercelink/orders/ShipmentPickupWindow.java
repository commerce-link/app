package pl.commercelink.orders;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBDocument;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * The window a courier pickup was ordered for, kept as text the way it is shown: the day as yyyy-MM-dd, the hours as
 * HH:mm. (Not the integration's {@code PickupWindow}, which is a record the DynamoDB mapper cannot store.)
 * <p>Treat it as immutable: the setters exist only for the DynamoDB mapper.
 */
@DynamoDBDocument
@Getter
@Setter
@NoArgsConstructor
public class ShipmentPickupWindow {

    private static final DateTimeFormatter STORED_HOUR = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter SHOWN_HOUR = DateTimeFormatter.ofPattern("H:mm");

    @DynamoDBAttribute(attributeName = "date")
    private String date;
    @DynamoDBAttribute(attributeName = "from")
    private String from;
    @DynamoDBAttribute(attributeName = "to")
    private String to;

    private ShipmentPickupWindow(String date, String from, String to) {
        this.date = date;
        this.from = from;
        this.to = to;
    }

    public static ShipmentPickupWindow of(LocalDate date, LocalTime from, LocalTime to) {
        return new ShipmentPickupWindow(date.toString(), from.format(STORED_HOUR), to.format(STORED_HOUR));
    }

    /** The day as the pickup page offered it ("czw. 8 paź"); a value that does not parse is shown as stored. */
    public String formatDay(Locale locale) {
        try {
            return DateTimeFormatter.ofPattern("EEE d MMM", locale).format(LocalDate.parse(date));
        } catch (RuntimeException e) {
            return date;
        }
    }

    public String formatFrom() {
        return formatHour(from);
    }

    public String formatTo() {
        return formatHour(to);
    }

    private static String formatHour(String hour) {
        try {
            return SHOWN_HOUR.format(LocalTime.parse(hour));
        } catch (RuntimeException e) {
            return hour;
        }
    }
}
