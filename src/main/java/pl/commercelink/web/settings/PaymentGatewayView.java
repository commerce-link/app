package pl.commercelink.web.settings;

/**
 * A payment gateway of the store as its row on the payments page. {@code installed} is false when the store names a
 * gateway whose adapter is no longer deployed: customers choosing it get an error, so the row says so. {@code configured}
 * means every required setting is saved; nothing tests the connection.
 */
public record PaymentGatewayView(String name, String displayName, boolean isDefault, boolean installed,
                                 boolean configured, String editHref, String defaultHref, String disconnectHref) {

    public boolean usable() {
        return installed && configured;
    }
}
