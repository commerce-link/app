package pl.commercelink.marketplace.links;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConverter;

import java.time.Instant;

public class MarketplaceOfferLinkInstantConverter implements DynamoDBTypeConverter<String, Instant> {

    @Override
    public String convert(Instant instant) {
        return instant == null ? null : instant.toString();
    }

    @Override
    public Instant unconvert(String value) {
        return value == null ? null : Instant.parse(value);
    }
}
