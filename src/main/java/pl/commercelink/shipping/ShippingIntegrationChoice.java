package pl.commercelink.shipping;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import pl.commercelink.orders.Order;
import pl.commercelink.rest.client.HttpClientException;
import pl.commercelink.shipping.api.OrderReference;
import pl.commercelink.shipping.api.ShipmentProposal;
import pl.commercelink.shipping.api.ShippingProvider;
import pl.commercelink.stores.Store;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Which shipping integrations can ship an order, and which one the page suggests: Wysyłam z Allegro for an order
 * placed on Allegro whose delivery method Allegro ships (its proposal says so), the store's default integration for
 * everything else. Only orders choose: RMA, customer returns and the warehouse always ship through the default one.
 * Asks Allegro once per call (the proposal), so a page computes the options once per request.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShippingIntegrationChoice {

    public static final String ALLEGRO = "allegro";
    static final String ALLEGRO_MARKETPLACE = "Allegro";

    private final ShippingProviders providers;
    private final ShippingService shippingService;
    private final ShippingIntegrationNames names;

    public List<ShippingIntegrationOption> forOrder(Store store, Order order) {
        Locale locale = LocaleContextHolder.getLocale();
        String defaultName = store.defaultShippingIntegration();
        List<ShippingIntegrationOption> options = new ArrayList<>();
        for (String name : store.shippingIntegrationNames()) {
            if (name.equals(defaultName)) {
                options.add(shippingService.isAvailable(store)
                        ? ShippingIntegrationOption.available(name, names.of(name, locale), null)
                        : ShippingIntegrationOption.unavailable(name, names.of(name, locale),
                                "shipping.integration.reason.notConnected", null));
            } else if (ALLEGRO.equals(name)) {
                options.add(allegro(store, order, locale));
            }
            // another additional integration would not ship orders yet: it is not offered
        }
        Optional<String> suggestion = suggestedName(options, defaultName);
        return options.stream()
                .map(option -> suggestion.filter(option.name()::equals).isPresent() ? option.suggestedCopy() : option)
                .toList();
    }

    public Optional<ShippingIntegrationOption> suggested(List<ShippingIntegrationOption> options) {
        return options.stream().filter(ShippingIntegrationOption::suggested).findFirst();
    }

    /** The option the page shows: the requested one while it is available, else the suggested one, else none. */
    public static String selected(List<ShippingIntegrationOption> options, String requested) {
        return availableNamed(options, requested)
                .or(() -> options.stream().filter(ShippingIntegrationOption::suggested).findFirst())
                .map(ShippingIntegrationOption::name)
                .orElse(null);
    }

    public static Optional<ShippingIntegrationOption> availableNamed(List<ShippingIntegrationOption> options, String name) {
        return options.stream().filter(o -> o.available() && o.name().equals(name)).findFirst();
    }

    /** The order was placed on Allegro: only those can ship through Wysyłam z Allegro. Cheap, asks nobody. */
    public static boolean isAllegroOrder(Order order) {
        return order != null && order.isMarketplaceOrder()
                && StringUtils.equalsIgnoreCase(order.getSource().getName(), ALLEGRO_MARKETPLACE);
    }

    private static Optional<String> suggestedName(List<ShippingIntegrationOption> options, String defaultName) {
        Optional<ShippingIntegrationOption> allegro = availableNamed(options, ALLEGRO);
        if (allegro.isPresent()) {
            return Optional.of(ALLEGRO);
        }
        return availableNamed(options, defaultName).map(ShippingIntegrationOption::name)
                .or(() -> options.stream().filter(ShippingIntegrationOption::available)
                        .map(ShippingIntegrationOption::name).findFirst());
    }

    private ShippingIntegrationOption allegro(Store store, Order order, Locale locale) {
        String displayName = names.of(ALLEGRO, locale);
        if (!isAllegroOrder(order)) {
            return ShippingIntegrationOption.unavailable(ALLEGRO, displayName, "shipping.integration.reason.allegroOnly", null);
        }
        Optional<ShippingProvider> provider = providers.forName(store, ALLEGRO);
        if (provider.isEmpty()) {
            return ShippingIntegrationOption.unavailable(ALLEGRO, displayName, "shipping.integration.reason.notConnected", null);
        }
        ShipmentProposal proposal;
        try {
            proposal = provider.get().proposeShipment(new OrderReference(order.getSource().getName(),
                    order.getExternalOrderId(), order.getShortenedOrderId()));
        } catch (RuntimeException e) {
            log.warn("Wysyłam z Allegro proposal for order {} of store {} failed: {}", order.getOrderId(),
                    store.getStoreId(), e.getMessage(), e);
            return ShippingIntegrationOption.unavailable(ALLEGRO, displayName, reasonKeyOf(e), null);
        }
        if (!proposal.available()) {
            return ShippingIntegrationOption.unavailable(ALLEGRO, displayName, "shipping.integration.reason.proposal",
                    proposal.unavailableReason());
        }
        return ShippingIntegrationOption.available(ALLEGRO, displayName, proposal);
    }

    /** 403: the Allegro application has no consent for shipments; invalid_grant or 401: the connection expired. */
    static String reasonKeyOf(Throwable failure) {
        for (Throwable t = failure; t != null; t = t.getCause()) {
            if (t instanceof HttpClientException http) {
                if (http.getStatusCode() == 403) {
                    return "shipping.integration.reason.consent";
                }
                if (http.getStatusCode() == 401 || StringUtils.contains(http.getResponseBody(), "invalid_grant")) {
                    return "shipping.integration.reason.authLost";
                }
            }
        }
        return "shipping.integration.reason.error";
    }
}
