package pl.commercelink.receipts;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;
import pl.commercelink.provider.EventBindingRegistrar;
import pl.commercelink.provider.ProviderCallLimiter;
import pl.commercelink.provider.ProviderCallRejectedException;
import pl.commercelink.provider.api.AuthConfig;
import pl.commercelink.provider.api.EventBinding;
import pl.commercelink.provider.api.ProviderField;
import pl.commercelink.provider.api.WebhookOutcome;
import pl.commercelink.provider.api.WebhookStatusResponse;
import pl.commercelink.receipts.api.Receipt;
import pl.commercelink.receipts.api.ReceiptProvider;
import pl.commercelink.receipts.api.ReceiptProviderDescriptor;
import pl.commercelink.stores.StoresRepository;

import java.util.List;
import java.util.Map;

/**
 * Status webhooks of receipt providers at /Store/{storeId}/Webhooks/Receipts/{binding}. Executors (which re-read the
 * receipt at the provider) run inside the shared call limiter; when no capacity is free in time the call is skipped
 * rather than failed, since polling covers it and a failing webhook would be retried and eventually disable the
 * whole provider account. REJECTED (bad token) answers 401 (handled by {@link EventBindingRegistrar} itself); any
 * authentic call answers 200, matched or not, and applying the result never lets an exception reach the caller.
 */
@Slf4j
@Configuration
public class ReceiptWebhookRegistry {

    public static final String WEBHOOK_PATH_PREFIX = "/Store/{storeId}/Webhooks/Receipts/";

    private final RouterFunction<ServerResponse> routes;

    ReceiptWebhookRegistry(ReceiptProviderFactory providerFactory, StoresRepository storesRepository,
                           ReceiptStatusUpdates updates, ProviderCallLimiter limiter) {
        List<ReceiptProviderDescriptor> descriptors = providerFactory.availableProviders().stream()
                .map(d -> (ReceiptProviderDescriptor) new Limited(d, limiter))
                .toList();
        this.routes = EventBindingRegistrar.forDescriptors(descriptors)
                .<Receipt>withWebhooks(WEBHOOK_PATH_PREFIX,
                        (descriptor, storeId) -> providerFactory.loadConfiguration(storesRepository.findById(storeId), descriptor.name()),
                        (descriptor, storeId, receipt) -> applyPushed(updates, descriptor, storeId, receipt))
                .register();
    }

    @Bean
    RouterFunction<ServerResponse> receiptWebhookRoutes() {
        return routes;
    }

    /**
     * applyPushed itself never throws (see {@link ReceiptStatusUpdates}), but the webhook answer must not depend on
     * that promise holding forever: an exception escaping here would turn an authentic call into a 500, which
     * Fakturownia retries and, after enough failures, disables the webhook for the whole account.
     */
    private static void applyPushed(ReceiptStatusUpdates updates, ReceiptProviderDescriptor descriptor,
                                    String storeId, Receipt receipt) {
        try {
            updates.applyPushed(storeId, descriptor.name(), receipt);
        } catch (RuntimeException e) {
            log.error("Receipt webhook result handler for store {} provider {} failed unexpectedly",
                    storeId, descriptor.name(), e);
        }
    }

    /** The provider's descriptor with every webhook executor run inside the call limiter and rejections logged. */
    private record Limited(ReceiptProviderDescriptor delegate, ProviderCallLimiter limiter) implements ReceiptProviderDescriptor {

        @Override
        public String name() {
            return delegate.name();
        }

        @Override
        public String displayName() {
            return delegate.displayName();
        }

        @Override
        public List<ProviderField> configurationFields() {
            return delegate.configurationFields();
        }

        @Override
        public ReceiptProvider create(Map<String, String> configuration) {
            return delegate.create(configuration);
        }

        @Override
        public ReceiptProvider create(Map<String, String> configuration, Map<String, Object> context) {
            return delegate.create(configuration, context);
        }

        @Override
        public Map<String, String> metadata() {
            return delegate.metadata();
        }

        @Override
        public AuthConfig authConfig() {
            return delegate.authConfig();
        }

        @Override
        @SuppressWarnings({"unchecked", "rawtypes"})
        public List<EventBinding<?>> bindings() {
            return delegate.bindings().stream().map(binding -> {
                if (!(binding instanceof EventBinding.WebhookBinding<?> webhook)) {
                    return binding;
                }
                EventBinding.WebhookBinding<Receipt> typed = (EventBinding.WebhookBinding<Receipt>) webhook;
                return (EventBinding<?>) new EventBinding.WebhookBinding<Receipt>(typed.path(), (body, context) -> {
                    WebhookOutcome<Receipt> outcome;
                    try {
                        outcome = limiter.call(name(), () -> typed.executor().execute(body, context));
                    } catch (ProviderCallRejectedException e) {
                        log.warn("Receipt webhook of {} skipped: no call capacity; polling covers it", name());
                        return WebhookOutcome.empty();
                    }
                    if (outcome.responseBody() instanceof WebhookStatusResponse status
                            && EventBindingRegistrar.REJECTED_STATUS.equals(status.status())) {
                        log.warn("Receipt webhook of {} rejected (wrong token?) — Fakturownia disables the webhook after 25 failures", name());
                    }
                    return outcome;
                });
            }).toList();
        }
    }
}
