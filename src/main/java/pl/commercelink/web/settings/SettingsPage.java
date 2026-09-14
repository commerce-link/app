package pl.commercelink.web.settings;

import pl.commercelink.starter.security.UserRole;
import pl.commercelink.web.nav.StorePath;

/** The settings page being rendered, as the tile that leads to it and the settings home to return to. */
public record SettingsPage(SettingsTile tile, String homeHref) {

    public static SettingsPage forTile(String relativePath, UserRole role, String storeId) {
        SettingsTile tile = StoreSettingsCatalog.tileAt(relativePath).orElseThrow();
        String homeHref = StoreSettingsCatalog.homeHref(role, storeId);
        return new SettingsPage(tile, homeHref);
    }

    public static SettingsPage forRequest(UserRole role, String path) {
        if (role == null || path == null || !path.startsWith(StoreSettingsCatalog.HOME_PATH + "/")) {
            return null;
        }
        String storeId = StorePath.storeIdIn(path);
        if (storeId != null) {
            if (role != UserRole.SUPER_ADMIN) {
                return null;
            }
            String homeHref = StoreSettingsCatalog.homeHref(role, storeId);
            return StoreSettingsCatalog.tileAt(path.substring(homeHref.length()))
                    .map(tile -> new SettingsPage(tile, homeHref))
                    .orElse(null);
        }
        // the super admin opens /dashboard/store/rma-centers from the Administration menu; there is no store
        // whose settings they could return to, so the page keeps its title and loses the back link
        String homeHref = role == UserRole.ADMIN ? StoreSettingsCatalog.HOME_PATH : null;
        return StoreSettingsCatalog.tileAt(path.substring(StoreSettingsCatalog.HOME_PATH.length()))
                .map(tile -> new SettingsPage(tile, homeHref))
                .orElse(null);
    }
}
