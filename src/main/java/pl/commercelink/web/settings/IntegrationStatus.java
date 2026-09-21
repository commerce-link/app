package pl.commercelink.web.settings;

/**
 * The provider a single-instance integration uses. {@code configured} means every required setting is saved; nothing
 * tests the connection, so the page says "configured", never "connected" (the old panel showed "Connected" for any
 * provider name). {@code installed} is false when the store names a provider whose adapter is no longer on the classpath.
 *
 * @param providerName null when no provider is chosen
 */
public record IntegrationStatus(String providerName, String displayName, boolean installed, boolean configured) {

    public static IntegrationStatus none() {
        return new IntegrationStatus(null, null, false, false);
    }

    public boolean chosen() {
        return providerName != null;
    }
}
