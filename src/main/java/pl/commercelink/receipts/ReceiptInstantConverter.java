package pl.commercelink.receipts;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConverter;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/** Instants as ISO-8601 UTC strings of fixed length (whole seconds), so they sort as text in the due index. */
public class ReceiptInstantConverter implements DynamoDBTypeConverter<String, Instant> {

    public static String format(Instant instant) {
        return DateTimeFormatter.ISO_INSTANT.format(instant.truncatedTo(ChronoUnit.SECONDS));
    }

    @Override
    public String convert(Instant instant) {
        return instant == null ? null : format(instant);
    }

    @Override
    public Instant unconvert(String value) {
        return value == null ? null : Instant.parse(value);
    }
}
