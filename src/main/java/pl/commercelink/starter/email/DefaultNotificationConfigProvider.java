package pl.commercelink.starter.email;

import org.springframework.stereotype.Component;
import pl.commercelink.orders.notifications.EmailNotificationType;
import pl.commercelink.stores.ClientNotificationsConfig;
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
    public ClientNotificationsConfig getConfig(String storeId) {
        return getStore(storeId).getClientNotificationsConfig();
    }

    @Override
    public boolean supports(String storeId, EmailNotificationType type) {
        ClientNotificationsConfig config = getConfig(storeId);
        return config != null && config.supports(type);
    }
}
