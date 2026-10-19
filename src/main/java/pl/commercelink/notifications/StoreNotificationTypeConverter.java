package pl.commercelink.notifications;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConverter;
import pl.commercelink.stores.StoreNotificationType;

import java.util.Arrays;

/**
 * Stores the type by its name and reads a name this version does not know (a type added by a newer version, then rolled
 * back) as {@code null}. {@code @DynamoDBTypeConvertedEnum} throws on it instead, and one such record failed the whole
 * list of the store's notifications (bell and notifications page answered 500); a {@code null} type is shown with the
 * default title and its stored message.
 */
public class StoreNotificationTypeConverter implements DynamoDBTypeConverter<String, StoreNotificationType> {

    @Override
    public String convert(StoreNotificationType type) {
        return type == null ? null : type.name();
    }

    @Override
    public StoreNotificationType unconvert(String name) {
        return Arrays.stream(StoreNotificationType.values())
                .filter(type -> type.name().equals(name))
                .findFirst()
                .orElse(null);
    }
}
