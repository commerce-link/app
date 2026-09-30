package pl.commercelink.stores;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBDocument;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConvertedEnum;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** Why and since when a store is inactive, and which of the one-off steps of the deactivation are already done. */
@DynamoDBDocument
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class StoreDeactivation {

    @DynamoDBTypeConvertedEnum
    private DeactivationReason reason;
    private String deactivatedAt;
    private String offersWithdrawnAt;
    private String ownerNotifiedAt;

    public static StoreDeactivation of(DeactivationReason reason, Instant deactivatedAt) {
        return new StoreDeactivation(reason, deactivatedAt.toString(), null, null);
    }
}
