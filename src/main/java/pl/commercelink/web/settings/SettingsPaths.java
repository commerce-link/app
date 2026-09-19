package pl.commercelink.web.settings;

import pl.commercelink.starter.security.CustomSecurityContext;

/**
 * Addresses of store settings pages. A store admin works on the store from their session and uses the short
 * addresses; a super admin works on the store named in the path. Settings forms post back to these addresses,
 * never to a store id taken from the submitted form.
 */
public final class SettingsPaths {

    /** Header sent by static/js/async-form.js; such a save gets the re-rendered form back instead of a redirect. */
    public static final String ASYNC_HEADER = "X-Requested-With";

    private static final String ASYNC_HEADER_VALUE = "fetch";

    private SettingsPaths() {
    }

    public static String store(String storeId, String relativePath) {
        return CustomSecurityContext.hasRole("SUPER_ADMIN")
                ? StoreSettingsCatalog.HOME_PATH + "/" + storeId + relativePath
                : StoreSettingsCatalog.HOME_PATH + relativePath;
    }

    public static boolean isAsync(String requestedWith) {
        return ASYNC_HEADER_VALUE.equals(requestedWith);
    }
}
