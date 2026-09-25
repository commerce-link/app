package pl.commercelink.provider;

/** No permit to call the provider came in time: the call was not made. */
public class ProviderCallRejectedException extends RuntimeException {

    public ProviderCallRejectedException(String providerName) {
        super("No capacity to call provider " + providerName + " in time; the call was not made");
    }
}
