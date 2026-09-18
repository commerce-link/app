package pl.commercelink.starter.email;

public interface NotificationConfigProvider {

    /** Notification settings of the store, or null when the store does not exist. */
    NotificationSettings settings(String storeId);
}
