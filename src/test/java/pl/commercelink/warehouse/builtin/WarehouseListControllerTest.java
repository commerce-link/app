package pl.commercelink.warehouse.builtin;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.util.LinkedMultiValueMap;
import pl.commercelink.products.ProductCatalog;
import pl.commercelink.products.ProductCatalogRepository;
import pl.commercelink.products.CategoryDefinition;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.stores.IntegrationType;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WarehouseListControllerTest {

    private static final String STORE_ID = "store-1";

    @Mock
    private WarehouseListService warehouseListService;
    @Mock
    private StoresRepository storesRepository;
    @Mock
    private ProductCatalogRepository productCatalogRepository;

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

    @Test
    void adminGetsTheRestockFormWithCatalogsSortedByNameAndTheirCategories() {
        // given
        Store store = mock(Store.class);
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        ProductCatalog peripherals = catalog("c2", "Peryferia", List.of());
        ProductCatalog computers = catalog("c1", "Komputery", List.of(category("k1", "GPU")));
        when(productCatalogRepository.findAll(STORE_ID)).thenReturn(List.of(peripherals, computers));
        ExtendedModelMap model = new ExtendedModelMap();
        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);
            security.when(() -> CustomSecurityContext.hasRole("ADMIN")).thenReturn(true);

            // when
            warehouseController.warehouseItems(new LinkedMultiValueMap<>(), Locale.ENGLISH, model);

            // then
            RestockForm restock = (RestockForm) model.getAttribute("restock");
            assertThat(restock.catalogs()).containsExactly(computers, peripherals);
            assertThat(restock.categoriesByCatalog().get("c1")).containsExactly(java.util.Map.of("id", "k1", "name", "GPU"));
            assertThat(restock.categoriesByCatalog().get("c2")).isEmpty();
            assertThat(restock.error()).isNull();
        }
    }

    @Test
    void nonAdminGetsNoRestockForm() {
        // given
        Store store = mock(Store.class);
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        ExtendedModelMap model = new ExtendedModelMap();
        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);
            security.when(() -> CustomSecurityContext.hasRole("ADMIN")).thenReturn(false);

            // when
            warehouseController.warehouseItems(new LinkedMultiValueMap<>(), Locale.ENGLISH, model);

            // then
            assertThat(model.containsAttribute("restock")).isFalse();
        }
    }

    @Test
    void fragmentEndpointDoesNotQueryCatalogsNorExposeTheRestockForm() {
        // given
        Store store = mock(Store.class);
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        ExtendedModelMap model = new ExtendedModelMap();
        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);
            security.when(() -> CustomSecurityContext.hasRole("ADMIN")).thenReturn(true);

            // when
            warehouseController.warehouseList(new LinkedMultiValueMap<>(), Locale.ENGLISH, model);

            // then
            assertThat(model.containsAttribute("restock")).isFalse();
            verifyNoInteractions(productCatalogRepository);
        }
    }

    @Test
    void restockFormToleratesCatalogsAndCategoriesWithoutNames() {
        // given
        Store store = mock(Store.class);
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        ProductCatalog unnamed = catalog("c3", null, List.of(category("k2", null), category(null, "No id"), category("k3", "CPU")));
        ProductCatalog named = catalog("c1", "Komputery", List.of());
        ProductCatalog withoutId = catalog(null, "Bez id", List.of());
        when(productCatalogRepository.findAll(STORE_ID)).thenReturn(List.of(unnamed, withoutId, named));
        ExtendedModelMap model = new ExtendedModelMap();
        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);
            security.when(() -> CustomSecurityContext.hasRole("ADMIN")).thenReturn(true);

            // when
            warehouseController.warehouseItems(new LinkedMultiValueMap<>(), Locale.ENGLISH, model);

            // then
            RestockForm restock = (RestockForm) model.getAttribute("restock");
            assertThat(restock.catalogs()).containsExactly(named, unnamed);
            assertThat(restock.categoriesByCatalog().get("c3")).containsExactly(java.util.Map.of("id", "k3", "name", "CPU"));
        }
    }

    private static ProductCatalog catalog(String id, String name, List<CategoryDefinition> categories) {
        ProductCatalog catalog = new ProductCatalog();
        catalog.setCatalogId(id);
        catalog.setName(name);
        catalog.setCategories(categories);
        return catalog;
    }

    private static CategoryDefinition category(String id, String name) {
        CategoryDefinition category = new CategoryDefinition();
        category.setCategoryId(id);
        category.setName(name);
        return category;
    }
}
