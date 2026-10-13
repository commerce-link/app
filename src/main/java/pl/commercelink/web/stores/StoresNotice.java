package pl.commercelink.web.stores;

import java.io.Serializable;

/** The outcome of an action shown above the stores list; a flash attribute, so it must survive the session store. */
public record StoresNotice(String tone, String icon, String text) implements Serializable {

    public static StoresNotice ok(String text) {
        return new StoresNotice("is-ok", "fa-check-circle", text);
    }

    public static StoresNotice warn(String text) {
        return new StoresNotice("is-warn", "fa-exclamation-triangle", text);
    }

    public static StoresNotice bad(String text) {
        return new StoresNotice("is-bad", "fa-exclamation-circle", text);
    }
}
