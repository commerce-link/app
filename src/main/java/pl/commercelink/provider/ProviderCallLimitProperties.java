package pl.commercelink.provider;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.Map;

/**
 * Limits shared by every call to one provider API from this instance, e.g. Fakturownia allows 1000 requests a minute
 * and two concurrent requests per IP address, and all stores (receipts and invoicing) call it from the same address.
 */
@ConfigurationProperties(prefix = "providers.call-limits")
public record ProviderCallLimitProperties(
        @DefaultValue("PT2M") Duration acquireTimeout,
        Map<String, Limit> limits) {

    public ProviderCallLimitProperties {
        limits = limits == null ? Map.of() : Map.copyOf(limits);
    }

    public record Limit(int maxConcurrent, int perMinute) {
        public Limit {
            if (maxConcurrent < 1 || perMinute < 1) {
                throw new IllegalArgumentException("provider call limits must be positive");
            }
        }
    }
}
