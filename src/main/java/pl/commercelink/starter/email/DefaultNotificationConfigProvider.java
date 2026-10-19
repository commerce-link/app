package pl.commercelink.starter.email;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import pl.commercelink.stores.ClientNotificationsConfiguration;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

@Component
public class DefaultNotificationConfigProvider implements NotificationConfigProvider {

    private final StoresRepository storesRepository;

    public DefaultNotificationConfigProvider(StoresRepository storesRepository) {
        this.storesRepository = storesRepository;
    }

    // No fallback to the store name or the company email: the store decides how its emails are signed and where
    // replies go, and until it has, nothing is sent.
    @Override
    public NotificationSettings settings(String storeId) {
        Store store = storesRepository.findById(storeId);
        if (store == null) {
            return null;
        }
        ClientNotificationsConfiguration configuration = store.getClientNotificationsConfiguration();
        if (configuration == null) {
            return new NotificationSettings(null, null, null);
        }
        return new NotificationSettings(configuration, StringUtils.trimToNull(configuration.getSenderName()),
                StringUtils.trimToNull(configuration.getReplyToEmail()));
    }
}
