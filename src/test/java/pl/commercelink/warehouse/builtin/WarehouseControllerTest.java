package pl.commercelink.warehouse.builtin;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;
import pl.commercelink.inventory.deliveries.DeliveredPredicate;
import pl.commercelink.inventory.supplier.SupplierLabels;
import pl.commercelink.orders.fulfilment.FulfilmentForm;
import pl.commercelink.orders.fulfilment.ManualWarehouseFulfilment;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.warehouse.RestockScope;
import pl.commercelink.warehouse.RestockSuggestionService;

import java.util.Collections;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WarehouseControllerTest {

    private static final String STORE_ID = "store-1";

    @Mock
    private WarehouseRepository warehouseRepository;
    @Mock
    private StoresRepository storesRepository;
    @Mock
    private ManualWarehouseFulfilment manualWarehouseFulfilment;
    @Mock
    private RestockSuggestionService restockSuggestionService;
    @Mock
    private WarehouseGoodsOutService warehouseGoodsOutService;
    @Mock
    private DeliveredPredicate deliveredPredicate;
    @Mock
    private WarehouseGoodsInService warehouseGoodsInService;
    @Mock
    private WarehouseInternalIssueService warehouseInternalIssueService;
    @Mock
    private WarehouseInternalReservationService warehouseInternalReservationService;
    @Mock
    private WarehouseAllocationsManager warehouseAllocationsManager;
    @Mock
    private SupplierLabels supplierLabels;

    @InjectMocks
    private WarehouseController warehouseController;

    @Test
    @DisplayName("restock exposes supplierLabels so fulfilment.html can resolve connection labels")
    void restockExposesSupplierLabelsForTheFulfilmentScreen() {
        // given
        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);

            when(restockSuggestionService.suggestForRestock(eq(STORE_ID), anyString(), isNull(),
                    eq(RestockScope.WholeCatalog), eq(false), isNull()))
                    .thenReturn(Collections.emptyList());
            when(manualWarehouseFulfilment.init(eq(STORE_ID), anyList())).thenReturn(new FulfilmentForm());
            var labels = new SupplierLabels(mock(StoresRepository.class)).forStore(null);
            when(supplierLabels.forStoreId(STORE_ID)).thenReturn(labels);

            Model model = new ConcurrentModel();

            // when
            String view = warehouseController.restock("catalog-1", null, RestockScope.WholeCatalog, null, false, model, Locale.ENGLISH, new MockHttpServletResponse());

            // then
            assertThat(view).isEqualTo("fulfilment");
            assertThat(model.getAttribute("supplierLabels")).isSameAs(labels);
        }
    }
}
