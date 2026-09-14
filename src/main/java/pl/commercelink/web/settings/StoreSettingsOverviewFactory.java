package pl.commercelink.web.settings;

import org.springframework.stereotype.Component;
import pl.commercelink.starter.security.UserRole;
import pl.commercelink.stores.Store;

/**
 * Describes the settings of a store for the settings home page: the sections and tiles visible to the
 * given role, with a link to each tile's own settings page.
 */
@Component
public class StoreSettingsOverviewFactory {

    public StoreSettingsOverview build(Store store, UserRole role) {
        String homeHref = StoreSettingsCatalog.homeHref(role, store.getStoreId());
        return new StoreSettingsOverview(StoreSettingsCatalog.sectionsFor(role, homeHref));
    }
}
