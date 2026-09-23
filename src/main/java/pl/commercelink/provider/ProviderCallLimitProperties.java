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
        // Queue-driven invoicing (order-invoicing-queue.fifo, 30 s visibility, maxReceiveCount 1) must never wait
        // longer than the message stays invisible, or the queue redelivers it while this call is still queued.
        @DefaultValue("PT20S") Duration invoicingAcquireTimeout,
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
