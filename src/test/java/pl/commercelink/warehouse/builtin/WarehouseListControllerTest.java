package pl.commercelink.warehouse.builtin;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.util.LinkedMultiValueMap;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.stores.IntegrationType;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WarehouseListControllerTest {

    private static final String STORE_ID = "store-1";

    @Mock
    private WarehouseListService warehouseListService;
    @Mock
    private StoresRepository storesRepository;

    @InjectMocks
    private WarehouseController warehouseController;

    @Test
    void pageAndFragmentUseTheStoreAndItsWms() {
        // given
        Store store = mock(Store.class);
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        when(store.hasIntegration(IntegrationType.WMS_PROVIDER)).thenReturn(true);
        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);
            security.when(() -> CustomSecurityContext.hasRole("ADMIN")).thenReturn(true);
            Locale pl = Locale.forLanguageTag("pl");

            // when
            String pageView = warehouseController.warehouseItems(new LinkedMultiValueMap<>(), pl, new ExtendedModelMap());
            String fragmentView = warehouseController.warehouseList(new LinkedMultiValueMap<>(), pl, new ExtendedModelMap());

            // then
            assertThat(pageView).isEqualTo("warehouse");
            assertThat(fragmentView).isEqualTo("warehouse :: results");
            verify(warehouseListService, times(2)).page(eq(STORE_ID), eq(true), eq(true), argThat(WarehouseListQuery::wms), eq(pl));
        }
    }

    @Test
    void pageExposesTheServiceModelAsPage() {
        // given
        Store store = mock(Store.class);
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        WarehousePageModel page = mock(WarehousePageModel.class);
        when(warehouseListService.page(eq(STORE_ID), eq(false), eq(false), any(), any())).thenReturn(page);
        ExtendedModelMap model = new ExtendedModelMap();
        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);

            // when
            warehouseController.warehouseItems(new LinkedMultiValueMap<>(), Locale.ENGLISH, model);

            // then
            assertThat(model.getAttribute("page")).isSameAs(page);
        }
    }
}
