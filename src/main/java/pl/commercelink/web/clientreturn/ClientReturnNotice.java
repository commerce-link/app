package pl.commercelink.web.clientreturn;

import java.io.Serializable;

/**
 * The outcome of a return form submission, shown inside one card of the return page after the redirect; a flash attribute,
 * i.e. kept in the HTTP session, hence serializable. {@code target} is {@code "form"} or {@code "parcel"}, {@code tone} is
 * {@code "ok"}, {@code "info"} or {@code "bad"}.
 */
public record ClientReturnNotice(String target, String tone, String text) implements Serializable {

    public static final String FORM = "form";
    public static final String PARCEL = "parcel";

    public static ClientReturnNotice ok(String target, String text) {
        return new ClientReturnNotice(target, "ok", text);
    }

    public static ClientReturnNotice info(String target, String text) {
        return new ClientReturnNotice(target, "info", text);
    }

    public static ClientReturnNotice bad(String target, String text) {
        return new ClientReturnNotice(target, "bad", text);
    }
}
