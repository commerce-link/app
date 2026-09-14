package pl.commercelink.web.settings;

import org.springframework.stereotype.Component;
import pl.commercelink.starter.security.UserRole;
import pl.commercelink.stores.Store;

import java.util.List;

/**
 * Describes the settings of a store for the settings home page: the sections and tiles visible to the
 * given role, with a link to each tile's own settings page.
 */
@Component
public class StoreSettingsOverviewFactory {

    public StoreSettingsOverview build(Store store, UserRole role) {
        String homeHref = StoreSettingsCatalog.homeHref(role, store.getStoreId());
        List<SettingsSectionView> sections = StoreSettingsCatalog.sections().stream()
                .map(section -> new SettingsSectionView(section.messageKey(), section.tiles().stream()
                        .filter(tile -> tile.visibleFor(role))
                        .map(tile -> new SettingsTileView(tile, homeHref + tile.relativePath()))
                        .toList()))
                .filter(section -> !section.tiles().isEmpty())
                .toList();
        return new StoreSettingsOverview(sections);
    }
}
