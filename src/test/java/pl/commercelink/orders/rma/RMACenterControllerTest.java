package pl.commercelink.orders.rma;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.ui.ExtendedModelMap;
import pl.commercelink.inventory.supplier.SupplierLabels;
import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.stores.ConnectionMode;
import pl.commercelink.stores.FulfilmentConfiguration;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoreSupplierConnection;
import pl.commercelink.stores.StoresRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RMACenterControllerTest {

    @Mock
    private StoresRepository storesRepository;
    @Mock
    private RMACentersRepository rmaCentersRepository;
    @Mock
    private SupplierRegistry supplierRegistry;
    @Mock
    private SupplierLabels supplierLabels;

    @InjectMocks
    private RMACenterController controller;

    private Store storeWith(StoreSupplierConnection... connections) {
        FulfilmentConfiguration config = new FulfilmentConfiguration();
        config.setSupplierConnections(new ArrayList<>(List.of(connections)));
        Store store = new Store();
        store.setStoreId("store-1");
        store.setFulfilmentConfiguration(config);
        return store;
    }

    private RMACenter defaultCenterFor(String provider) {
        RMACenter center = new RMACenter();
        center.setStoreId(RMACenter.MANAGED_RMA_CENTER_STORE_ID);
        center.setRmaCenterId("center-1");
        center.setProvider(provider);
        return center;
    }

    @SuppressWarnings("unchecked")
    private List<RMACenter> renderedCenters(ExtendedModelMap model) {
        return ((Stream<RMACenter>) model.getAttribute("rmaCenters")).toList();
    }

    @Test
    void listShowsAPlatformCenterToAStoreWithATokenedInstanceOfItsType() {
        // given
        when(rmaCentersRepository.findByStoreId("store-1")).thenReturn(List.of(defaultCenterFor("Elko")));
        when(storesRepository.findById("store-1"))
                .thenReturn(storeWith(new StoreSupplierConnection("Elko-k7f3a9c2", ConnectionMode.OWN, true, true)));
        when(supplierRegistry.exists("Elko")).thenReturn(true);
        ExtendedModelMap model = new ExtendedModelMap();

        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(() -> CustomSecurityContext.hasRole("SUPER_ADMIN")).thenReturn(false);
            security.when(() -> CustomSecurityContext.hasRole("ADMIN")).thenReturn(true);
            security.when(CustomSecurityContext::getStoreId).thenReturn("store-1");

            // when
            controller.list(model);
        }

        // then
        assertThat(renderedCenters(model)).extracting(RMACenter::getProvider).containsExactly("Elko");
    }

    @Test
    void listHidesAPlatformCenterFromAStoreWithoutAConnectionOfItsType() {
        // given
        when(rmaCentersRepository.findByStoreId("store-1")).thenReturn(List.of(defaultCenterFor("Elko")));
        when(storesRepository.findById("store-1"))
                .thenReturn(storeWith(new StoreSupplierConnection("Kosatec-a1b2c3d4", ConnectionMode.OWN, true, true)));
        when(supplierRegistry.exists("Elko")).thenReturn(true);
        ExtendedModelMap model = new ExtendedModelMap();

        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(() -> CustomSecurityContext.hasRole("SUPER_ADMIN")).thenReturn(false);
            security.when(() -> CustomSecurityContext.hasRole("ADMIN")).thenReturn(true);
            security.when(CustomSecurityContext::getStoreId).thenReturn("store-1");

            // when
            controller.list(model);
        }

        // then
        assertThat(renderedCenters(model)).isEmpty();
    }
}
