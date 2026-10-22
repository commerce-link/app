package pl.commercelink.web.clientorder;

import java.io.Serializable;

/**
 * The outcome shown inside one card of the order page after a redirect; a flash attribute, so it must survive the session store.
 * {@code target} is {@code "delivery"} or {@code "date"}, {@code tone} is {@code "ok"} or {@code "bad"} and {@code field}
 * names the input the message belongs to ({@code "code"}) or is null when the message concerns the whole card.
 */
public record ClientOrderNotice(String target, String tone, String text, String field) implements Serializable {

    public static final String DELIVERY = "delivery";
    public static final String DATE = "date";
    public static final String CODE_FIELD = "code";

    public static ClientOrderNotice ok(String target, String text) {
        return new ClientOrderNotice(target, "ok", text, null);
    }

    public static ClientOrderNotice bad(String target, String text) {
        return new ClientOrderNotice(target, "bad", text, null);
    }

    public static ClientOrderNotice codeError(String text) {
        return new ClientOrderNotice(DELIVERY, "bad", text, CODE_FIELD);
    }
}
