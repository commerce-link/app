package pl.commercelink.web.orders;

import java.io.Serializable;

/** An outcome shown in the page body after a redirect (tone is-ok / is-warn), optionally with a link (B8: "View delivery"). */
public record OrderNotice(String tone, String text, String linkHref, String linkText) implements Serializable {
}
