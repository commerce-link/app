package pl.commercelink.starter.email;

import org.springframework.stereotype.Component;
import pl.commercelink.orders.notifications.EmailNotificationType;
import pl.commercelink.stores.ClientNotificationsConfiguration;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

@Component
public class DefaultNotificationConfigProvider implements NotificationConfigProvider {

    private final StoresRepository storesRepository;

    public DefaultNotificationConfigProvider(StoresRepository storesRepository) {
        this.storesRepository = storesRepository;
    }

    private Store getStore(String storeId) {
        return storesRepository.findById(storeId);
    }

    @Override
    public ClientNotificationsConfiguration getConfig(String storeId) {
        return getStore(storeId).getClientNotificationsConfiguration();
    }

    @Override
    public boolean supports(String storeId, EmailNotificationType type) {
        ClientNotificationsConfiguration config = getConfig(storeId);
        return config != null && config.supports(type);
    }
}
