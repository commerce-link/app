package pl.commercelink.shipping;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;
import pl.commercelink.provider.EventBindingRegistrar;
import pl.commercelink.shipping.api.ShippingWebhookResult;
import pl.commercelink.shipping.tracking.ShipmentTrackingState;
import pl.commercelink.shipping.tracking.ShipmentTrackingUpdates;
import pl.commercelink.stores.StoresRepository;

@Configuration
public class ShippingWebhookRegistry {

    private final StoresRepository storesRepository;
    private final ShipmentTrackingUpdates shipmentTrackingUpdates;
    private final RouterFunction<ServerResponse> routes;

    ShippingWebhookRegistry(ShippingProviderFactory shippingProviderFactory,
                            StoresRepository storesRepository,
                            ShipmentTrackingUpdates shipmentTrackingUpdates) {
        this.storesRepository = storesRepository;
        this.shipmentTrackingUpdates = shipmentTrackingUpdates;

        this.routes = EventBindingRegistrar.forDescriptors(shippingProviderFactory.availableProviders())
                .<ShippingWebhookResult>withWebhooks(
                        "/Store/{storeId}/Webhooks/Shipping/",
                        (descriptor, storeId) -> shippingProviderFactory.loadConfiguration(
                                storesRepository.findById(storeId), descriptor.name()),
                        (descriptor, storeId, result) -> processResult(storeId, result))
                .register();
    }

    @Bean
    RouterFunction<ServerResponse> shippingWebhookRoutes() {
        return routes;
    }

    private void processResult(String storeId, ShippingWebhookResult result) {
        if (storesRepository.findById(storeId) == null) {
            throw new RuntimeException("Internal error.");
        }
        ShipmentTrackingState state = switch (result.state()) {
            case COLLECTED -> ShipmentTrackingState.COLLECTED;
            case DELIVERED -> ShipmentTrackingState.DELIVERED;
            case OTHER -> null;
        };
        if (state != null) {
            shipmentTrackingUpdates.apply(storeId, result.trackingNo(), state, result.datetime());
        }
    }
}
