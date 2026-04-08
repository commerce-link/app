package pl.commercelink.starter.security.tenant;

public interface ApiKeyValidator {
    boolean isValid(String apiKey, String storeId);
}
