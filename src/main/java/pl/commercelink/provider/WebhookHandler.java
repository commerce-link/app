package pl.commercelink.provider;

import java.util.Map;

@FunctionalInterface
public interface WebhookHandler {
    void handle(Object event, String storeId, Map<String, String> headers);
}
