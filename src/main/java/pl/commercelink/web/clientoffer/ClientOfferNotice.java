package pl.commercelink.web.clientoffer;

/** The outcome shown on the offer page after a redirect; {@code tone} is {@code "ok"} or {@code "bad"}. */
public record ClientOfferNotice(String tone, String text) {

    public static ClientOfferNotice ok(String text) {
        return new ClientOfferNotice("ok", text);
    }

    public static ClientOfferNotice bad(String text) {
        return new ClientOfferNotice("bad", text);
    }
}
