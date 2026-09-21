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

    // Blank sender fields fall back to the store name and the company email, so a store that never filled the form
    // still signs its emails and receives replies.
    @Override
    public NotificationSettings settings(String storeId) {
        Store store = storesRepository.findById(storeId);
        if (store == null) {
            return null;
        }
        ClientNotificationsConfiguration configuration = store.getClientNotificationsConfiguration();
        String configuredName = configuration != null ? StringUtils.trimToNull(configuration.getSenderName()) : null;
        String senderName = configuredName != null ? configuredName : StringUtils.trimToNull(store.getName());
        return new NotificationSettings(configuration, senderName, StringUtils.trimToNull(store.getClientContactEmail()));
    }
}
