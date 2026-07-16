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
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;
import pl.commercelink.inventory.deliveries.DeliveredPredicate;
import pl.commercelink.orders.FulfilmentStatus;
import pl.commercelink.orders.fulfilment.ManualWarehouseFulfilment;
import pl.commercelink.products.ProductCatalogRepository;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.stores.IntegrationType;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.warehouse.RestockSuggestionService;
import pl.commercelink.warehouse.api.Warehouse;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
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
    private Warehouse warehouse;
    @Mock
    private WarehouseRepository warehouseRepository;
    @Mock
    private StoresRepository storesRepository;
    @Mock
    private ManualWarehouseFulfilment manualWarehouseFulfilment;
    @Mock
    private RestockSuggestionService restockSuggestionService;
    @Mock
    private ProductCatalogRepository productCatalogRepository;
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

    @InjectMocks
    private WarehouseController warehouseController;

    @Test
    @DisplayName("warehouseItems lists items without category first instead of failing with NPE")
    @SuppressWarnings("unchecked")
    void warehouseItemsListsItemsWithoutCategoryFirstInsteadOfFailing() {
        // given
        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);
            security.when(() -> CustomSecurityContext.hasRole("ADMIN")).thenReturn(true);

            Store store = mock(Store.class);
            when(store.hasIntegration(IntegrationType.WMS_PROVIDER)).thenReturn(false);
            when(storesRepository.findById(STORE_ID)).thenReturn(store);

            WarehouseItem withCategory = deliveredItem("CPU", "Ryzen 7");
            WarehouseItem withoutCategory = deliveredItem(null, "Uchwyt montazowy");
            when(warehouseRepository.findAllFiltered(eq(STORE_ID), isNull(), anyList()))
                    .thenReturn(List.of(withCategory, withoutCategory));
            when(warehouseRepository.findAllCategories(STORE_ID)).thenReturn(Collections.emptySet());
            when(productCatalogRepository.findAll(STORE_ID)).thenReturn(Collections.emptyList());

            Model model = new ConcurrentModel();

            // when
            String view = warehouseController.warehouseItems(null, null, false, model);

            // then
            assertThat(view).isEqualTo("warehouse");
            List<WarehouseItem> deliveredItems = (List<WarehouseItem>) model.getAttribute("deliveredItems");
            assertThat(deliveredItems).containsExactly(withoutCategory, withCategory);
        }
    }

    private WarehouseItem deliveredItem(String categoryKey, String name) {
        WarehouseItem item = new WarehouseItem();
        item.setStoreId(STORE_ID);
        item.setCategory(categoryKey);
        item.setName(name);
        item.setStatus(FulfilmentStatus.Delivered);
        return item;
    }
}
