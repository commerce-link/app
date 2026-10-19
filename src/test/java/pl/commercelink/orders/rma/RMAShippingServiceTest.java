package pl.commercelink.orders.rma;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pl.commercelink.orders.ShippingDetails;
import pl.commercelink.shipping.ShipmentCreationCheckRequest;
import pl.commercelink.shipping.ShipmentCreationService;
import pl.commercelink.shipping.ShipmentOwnerType;
import pl.commercelink.shipping.ShippingService;
import pl.commercelink.stores.AuthorizedCarrier;
import pl.commercelink.stores.PackageTemplate;
import pl.commercelink.stores.RMAConfiguration;
import pl.commercelink.stores.Store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RMAShippingServiceTest {

    @Mock private ShippingService shippingService;
    @Mock private ShipmentCreationService shipmentCreationService;
    @Mock private Store store;
    @Mock private RMAConfiguration configuration;
    @Mock private AuthorizedCarrier carrier;

    @InjectMocks
    private RMAShippingService service;

    @BeforeEach
    void setUp() {
        when(carrier.getName()).thenReturn("DPD");
        when(configuration.getCarrier()).thenReturn(carrier);
        when(store.getRmaConfiguration()).thenReturn(configuration);
        when(store.getStoreId()).thenReturn("store-1");
        PackageTemplate template = new PackageTemplate();
        template.setId("7");
        when(store.getPackageTemplate("7")).thenReturn(template);
    }

    private static RMAShipmentRequest request() {
        return new RMAShipmentRequest("rma-1", "7", ShippingDetails._default(), 100);
    }

    private ShipmentCreationCheckRequest seedSent() {
        ArgumentCaptor<ShipmentCreationCheckRequest> seed = ArgumentCaptor.forClass(ShipmentCreationCheckRequest.class);
        verify(shipmentCreationService).start(seed.capture(), any(), eq(store), any());
        return seed.getValue();
    }

    @Test
    void theCustomersSubmissionNeverReplacesAReturnAlreadyOnTheRma() {
        // when
        service.startReturnShipment(request(), store);

        // then
        ShipmentCreationCheckRequest seed = seedSent();
        assertThat(seed.getOwnerType()).isEqualTo(ShipmentOwnerType.RMA_RETURN);
        assertThat(seed.getOwnerId()).isEqualTo("rma-1");
        assertThat(seed.isReplacesFailedReturn()).isFalse();
    }

    @Test
    void theOperatorsRetryReplacesTheFailedReturn() {
        // when
        service.retryReturnShipment(request(), store);

        // then
        ShipmentCreationCheckRequest seed = seedSent();
        assertThat(seed.getOwnerType()).isEqualTo(ShipmentOwnerType.RMA_RETURN);
        assertThat(seed.isReplacesFailedReturn()).isTrue();
    }
}
