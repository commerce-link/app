package pl.commercelink.shipping;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import pl.commercelink.shipping.api.ShippingWebhookResult;
import pl.commercelink.shipping.tracking.ShipmentTrackingState;
import pl.commercelink.shipping.tracking.ShipmentTrackingUpdates;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ShippingWebhookRegistryTest {

    private static final String STORE_ID = "store-1";
    private static final LocalDateTime AT = LocalDateTime.of(2026, 9, 2, 13, 30);

    @Mock private ShippingProviderFactory shippingProviderFactory;
    @Mock private StoresRepository storesRepository;
    @Mock private ShipmentTrackingUpdates shipmentTrackingUpdates;
    @Mock private Store store;

    private ShippingWebhookRegistry registry;

    @BeforeEach
    void setUp() {
        when(shippingProviderFactory.availableProviders()).thenReturn(List.of());
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        registry = new ShippingWebhookRegistry(shippingProviderFactory, storesRepository, shipmentTrackingUpdates);
    }

    private void process(String storeId, ShippingWebhookResult result) {
        ReflectionTestUtils.invokeMethod(registry, "processResult", storeId, result);
    }

    @Test
    void collectedAndDeliveredGoThroughTheCommonPathWithTheCarrierTime() {
        // when
        process(STORE_ID, new ShippingWebhookResult("PKG-1", ShippingWebhookResult.ShipmentState.COLLECTED, AT));
        process(STORE_ID, new ShippingWebhookResult("PKG-1", ShippingWebhookResult.ShipmentState.DELIVERED, AT));

        // then
        verify(shipmentTrackingUpdates).apply(STORE_ID, "PKG-1", ShipmentTrackingState.COLLECTED, AT);
        verify(shipmentTrackingUpdates).apply(STORE_ID, "PKG-1", ShipmentTrackingState.DELIVERED, AT);
    }

    @Test
    void otherStatesAreIgnored() {
        // when
        process(STORE_ID, new ShippingWebhookResult("PKG-1", ShippingWebhookResult.ShipmentState.OTHER, AT));

        // then
        verifyNoInteractions(shipmentTrackingUpdates);
    }

    @Test
    void unknownStoreIsRejected() {
        assertThatThrownBy(() -> process("missing",
                new ShippingWebhookResult("PKG-1", ShippingWebhookResult.ShipmentState.DELIVERED, AT)))
                .isInstanceOf(RuntimeException.class);
        verifyNoInteractions(shipmentTrackingUpdates);
    }
}
