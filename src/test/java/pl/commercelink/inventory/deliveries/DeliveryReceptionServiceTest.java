package pl.commercelink.inventory.deliveries;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.documents.Document;
import pl.commercelink.invoicing.InvoicingProviderFactory;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.starter.util.OperationResult;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.stores.WarehouseConfiguration;
import pl.commercelink.warehouse.api.GoodsInHandler;
import pl.commercelink.warehouse.api.GoodsInRequest;
import pl.commercelink.warehouse.api.Warehouse;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeliveryReceptionServiceTest {

    private static final String STORE_ID = "store-1";
    private static final String DELIVERY_ID = "delivery-1";

    @Mock
    private InvoicingProviderFactory invoicingProviderFactory;
    @Mock
    private StoresRepository storesRepository;
    @Mock
    private Warehouse warehouse;
    @Mock
    private DeliveriesRepository deliveriesRepository;
    @Mock
    private GoodsInHandler goodsInHandler;
    @Mock
    private Delivery delivery;

    @InjectMocks
    private DeliveryReceptionService service;

    @Test
    void receivesDeliveryWithoutTouchingInvoicingWhenDocumentsGenerationIsDisabled() {
        // given
        Store store = storeWith(warehouseConfiguration(false, null));
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        when(warehouse.goodsInHandler(STORE_ID)).thenReturn(goodsInHandler);
        when(goodsInHandler.receive(any(), eq(false))).thenReturn(OperationResult.success());
        when(deliveriesRepository.findById(STORE_ID, DELIVERY_ID)).thenReturn(delivery);

        // when
        OperationResult<Document> result = receive();

        // then
        assertTrue(result.isSuccess());
        ArgumentCaptor<GoodsInRequest> captor = ArgumentCaptor.forClass(GoodsInRequest.class);
        verify(goodsInHandler).receive(captor.capture(), eq(false));
        assertNull(captor.getValue().getIssuer());
        assertNull(captor.getValue().getCounterparty());
        verify(invoicingProviderFactory, never()).get(any());
        verify(delivery).markAsReceived();
        verify(deliveriesRepository).save(delivery);
    }

    @Test
    void failsWithoutInvoicingProviderWhenDocumentsGenerationIsEnabled() {
        // given
        Store store = storeWith(warehouseConfiguration(true, "cost-center-1"));
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        when(invoicingProviderFactory.get(store)).thenReturn(null);

        // when
        OperationResult<Document> result = receive();

        // then
        assertFalse(result.isSuccess());
        verify(warehouse, never()).goodsInHandler(any());
        verify(deliveriesRepository, never()).save(any());
    }

    @Test
    void failsOnIncompleteWarehouseConfigurationWhenDocumentsGenerationIsEnabled() {
        // given
        Store store = storeWith(warehouseConfiguration(true, null));
        when(storesRepository.findById(STORE_ID)).thenReturn(store);

        // when
        OperationResult<Document> result = receive();

        // then
        assertFalse(result.isSuccess());
        verify(invoicingProviderFactory, never()).get(any());
    }

    private OperationResult<Document> receive() {
        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(CustomSecurityContext::getLoggedInUserName).thenReturn("user");
            return service.receive(STORE_ID, "Supplier", DELIVERY_ID, List.of(), List.of(), List.of());
        }
    }

    private Store storeWith(WarehouseConfiguration configuration) {
        Store store = new Store();
        store.setStoreId(STORE_ID);
        store.setWarehouseConfiguration(configuration);
        return store;
    }

    private WarehouseConfiguration warehouseConfiguration(boolean documentsGenerationEnabled, String costCenterId) {
        WarehouseConfiguration configuration = new WarehouseConfiguration();
        configuration.setWarehouseId("warehouse-1");
        configuration.setCostCenterId(costCenterId);
        configuration.setDocumentsGenerationEnabled(documentsGenerationEnabled);
        return configuration;
    }
}
