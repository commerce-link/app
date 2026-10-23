package pl.commercelink.stores;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBDocument;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIgnore;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@DynamoDBDocument
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class TrialPeriod {

    private String ownerEmail;
    private String startedAt;
    private String expiresAt;

    public static TrialPeriod starting(String ownerEmail, Instant now, int days) {
        return new TrialPeriod(ownerEmail, now.toString(), now.plus(days, ChronoUnit.DAYS).toString());
    }

    @DynamoDBIgnore
    public Optional<Instant> expiresAtInstant() {
        if (expiresAt == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(Instant.parse(expiresAt));
        } catch (DateTimeException e) {
            return Optional.empty();
        }
    }

    @DynamoDBIgnore
    public boolean isExpired(Instant now) {
        return expiresAtInstant().map(end -> end.isBefore(now)).orElse(false);
    }
}
