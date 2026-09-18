package pl.commercelink.web.settings;

/**
 * A destructive action confirmed on its own page when JavaScript is off; with JavaScript the same texts fill the
 * confirmation dialog opened from the link to this page. All texts are resolved messages.
 */
public record ConfirmAction(String title, String message, String confirmLabel, String actionPath, String cancelPath) {
}
