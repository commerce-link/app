package pl.commercelink.orders.rma;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.documents.Document;
import pl.commercelink.invoicing.InvoicingProviderFactory;
import pl.commercelink.orders.BillingDetails;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.starter.util.OperationResult;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.stores.WarehouseConfiguration;
import pl.commercelink.warehouse.api.ItemCondition;
import pl.commercelink.warehouse.api.RmaGoodsInHandler;
import pl.commercelink.warehouse.api.RmaGoodsInRequest;
import pl.commercelink.warehouse.api.Warehouse;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RmaGoodsInServiceTest {

    private static final String STORE_ID = "store-1";

    @Mock
    private StoresRepository storesRepository;
    @Mock
    private InvoicingProviderFactory invoicingProviderFactory;
    @Mock
    private Warehouse warehouse;
    @Mock
    private RmaGoodsInHandler rmaGoodsInHandler;

    @InjectMocks
    private RmaGoodsInService service;

    @Test
    void receivesItemsWithoutTouchingInvoicingWhenDocumentsGenerationIsDisabled() {
        // given
        Store store = storeWith(warehouseConfiguration(false, null));
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        when(warehouse.rmaGoodsInHandler(STORE_ID)).thenReturn(rmaGoodsInHandler);
        when(rmaGoodsInHandler.receive(any(), eq(false))).thenReturn(OperationResult.success());

        // when
        OperationResult<Document> result = receive(store);

        // then
        assertTrue(result.isSuccess());
        ArgumentCaptor<RmaGoodsInRequest> captor = ArgumentCaptor.forClass(RmaGoodsInRequest.class);
        verify(rmaGoodsInHandler).receive(captor.capture(), eq(false));
        assertFalse(captor.getValue().hasDocumentData());
        assertEquals(ItemCondition.OpenBox, captor.getValue().getItems().get(0).getCondition());
        verify(invoicingProviderFactory, never()).get(any());
    }

    @Test
    void failsWithoutInvoicingProviderWhenDocumentsGenerationIsEnabled() {
        // given
        Store store = storeWith(warehouseConfiguration(true, "cost-center-1"));
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        when(invoicingProviderFactory.get(store)).thenReturn(null);

        // when
        OperationResult<Document> result = receive(store);

        // then
        assertFalse(result.isSuccess());
        verify(warehouse, never()).rmaGoodsInHandler(any());
    }

    @Test
    void failsOnIncompleteWarehouseConfigurationWhenDocumentsGenerationIsEnabled() {
        // given
        Store store = storeWith(warehouseConfiguration(true, null));
        when(storesRepository.findById(STORE_ID)).thenReturn(store);

        // when
        OperationResult<Document> result = receive(store);

        // then
        assertFalse(result.isSuccess());
        verify(invoicingProviderFactory, never()).get(any());
    }

    private OperationResult<Document> receive(Store store) {
        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(CustomSecurityContext::getLoggedInUserName).thenReturn("user");
            return service.receive(STORE_ID, anRma(), List.of(anRmaItem()), new BillingDetails(), false, ItemCondition.OpenBox);
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

    private RMA anRma() {
        RMA rma = new RMA(STORE_ID);
        rma.setRmaId("rma-1");
        rma.setOrderId("order-1");
        return rma;
    }

    private RMAItem anRmaItem() {
        RMAItem rmaItem = new RMAItem();
        rmaItem.setDeliveryId("delivery-1");
        rmaItem.setMfn("MFN-1");
        rmaItem.setQty(1);
        return rmaItem;
    }
}
