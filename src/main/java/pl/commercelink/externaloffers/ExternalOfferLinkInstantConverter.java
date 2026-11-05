package pl.commercelink.externaloffers;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConverter;

import java.time.Instant;

public class ExternalOfferLinkInstantConverter implements DynamoDBTypeConverter<String, Instant> {

    @Override
    public String convert(Instant instant) {
        return instant == null ? null : instant.toString();
    }

    @Override
    public Instant unconvert(String value) {
        return value == null ? null : Instant.parse(value);
    }
}
