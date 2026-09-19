package pl.commercelink.web.settings;

/**
 * An action confirmed on its own page when JavaScript is off; with JavaScript the same texts fill the confirmation
 * dialog opened from the link to this page. All texts are resolved messages. The button is red only for an action that
 * removes something for good; a replaceable change (a new conversions address) confirms with the primary button.
 */
public record ConfirmAction(String title, String message, String confirmLabel, String actionPath, String cancelPath,
                            boolean destructive) {

    public ConfirmAction(String title, String message, String confirmLabel, String actionPath, String cancelPath) {
        this(title, message, confirmLabel, actionPath, cancelPath, true);
    }
}
