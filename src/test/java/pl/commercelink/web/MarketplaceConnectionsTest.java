package pl.commercelink.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pl.commercelink.marketplace.MarketplaceConnectionService;
import pl.commercelink.marketplace.MarketplaceProviderFactory;
import pl.commercelink.products.ProductCatalog;
import pl.commercelink.products.ProductCatalogRepository;
import pl.commercelink.stores.MarketplaceIntegration;
import pl.commercelink.stores.Store;
import pl.commercelink.web.settings.MarketplaceView;
import pl.commercelink.web.settings.MarketplaceView.State;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static pl.commercelink.web.MarketplacesFixture.PL;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MarketplaceConnectionsTest {

    private static final String PATH = "/dashboard/store/marketplaces";

    @Mock
    private MarketplaceProviderFactory providerFactory;
    @Mock
    private MarketplaceConnectionService connectionService;
    @Mock
    private MarketplaceAuthorization authorization;
    @Mock
    private ProductCatalogRepository catalogRepository;

    private MarketplaceConnections marketplaces;
    private Store store;

    @BeforeEach
    void setUp() {
        marketplaces = MarketplacesFixture.install(providerFactory, connectionService, authorization, catalogRepository,
                MarketplacesFixture.polishMessages());
        store = new Store();
        store.setStoreId("store-1");
    }

    @Test
    void anActiveMarketplaceShowsItsSchedulesLastFetchAndTheCatalogsSendingOffers() {
        // given
        MarketplaceIntegration allegro = store.connectMarketplace("Allegro", false);
        allegro.setOrdersImportSchedule("0/30 * * * ? *");
        allegro.setLastFetchedAt(LocalDateTime.of(2026, 9, 18, 14, 5));
        List<ProductCatalog> catalogs = List.of(
                catalog("c1", "Outlet", true), catalog("c2", "Główny", true), catalog("c3", "Hurt", false));
        when(catalogRepository.findAll("store-1")).thenReturn(catalogs);

        // when
        MarketplaceView view = marketplaces.views(store, PATH, PL).get(0);

        // then
        assertThat(view.state()).isEqualTo(State.ACTIVE);
        assertThat(view.orders()).isEqualTo("Zamówienia: Co 30 min · ostatnio pobrane 18.09.2026 14:05");
        assertThat(view.returns()).isEqualTo("Zwroty: Domyślny — co 60 min");
        assertThat(view.catalogs()).containsExactly("Główny", "Outlet");
        assertThat(view.editHref()).isEqualTo(PATH + "/Allegro");
        assertThat(view.authorizeHref()).isEqualTo(PATH + "/Allegro/authorize");
        assertThat(view.exportsHref()).isEqualTo(PATH + "/exports/Allegro");
        assertThat(view.needsAuthorization()).isFalse();
    }

    @Test
    void aMarketplaceWithoutReturnsImportHasNoReturnsLineNorAccountPage() {
        // given
        store.connectMarketplace("CsCart", false);

        // when
        MarketplaceView view = marketplaces.views(store, PATH, PL).get(0);

        // then
        assertThat(view.displayName()).isEqualTo("CS-Cart Multi-Vendor");
        assertThat(view.returns()).isNull();
        assertThat(view.authorizeHref()).isNull();
        assertThat(view.orders()).isEqualTo("Zamówienia: Domyślny — co 10 min · jeszcze nie pobierano");
        assertThat(view.catalogs()).isEmpty();
    }

    /** Saved but never connected: nothing is fetched, and the list offers "Connect account" where the status is. */
    @Test
    void anAccountNeverConnectedIsToldApartFromAnExpiredConnection() {
        // given
        store.connectMarketplace("Allegro", true);
        MarketplaceIntegration csCart = store.connectMarketplace("CsCart", false);
        csCart.setLoggedIn(false);
        csCart.setLastFetchedAt(LocalDateTime.now());

        // when
        List<MarketplaceView> views = marketplaces.views(store, PATH, PL);

        // then
        assertThat(views.get(0).state()).isEqualTo(State.NOT_AUTHORIZED);
        assertThat(views.get(0).needsAuthorization()).isTrue();
        assertThat(views.get(1).state()).isEqualTo(State.EXPIRED);
        // keys-only marketplaces are fixed by saving the keys again, not on an account page
        assertThat(views.get(1).needsAuthorization()).isFalse();
    }

    @Test
    void aMarketplaceWhoseAdapterIsGoneIsMissing() {
        // given
        store.connectMarketplace("Ceneo", false);

        // when
        MarketplaceView view = marketplaces.views(store, PATH, PL).get(0);

        // then
        assertThat(view.state()).isEqualTo(State.MISSING);
        assertThat(view.displayName()).isEqualTo("Ceneo");
        assertThat(view.installed()).isFalse();
    }

    @Test
    void aStoreWithoutMarketplacesDoesNotReadItsCatalogs() {
        // when
        List<MarketplaceView> views = marketplaces.views(store, PATH, PL);

        // then
        assertThat(views).isEmpty();
        verifyNoInteractions(catalogRepository);
    }

    @Test
    void onlyInstalledMarketplacesTheStoreDoesNotUseCanBeAdded() {
        // given
        store.connectMarketplace("Allegro", true);

        // when / then
        assertThat(marketplaces.addable(store)).extracting(d -> d.name()).containsExactly("CsCart");
    }

    private static ProductCatalog catalog(String id, String name, boolean exportsToAllegro) {
        ProductCatalog catalog = mock(ProductCatalog.class);
        when(catalog.getCatalogId()).thenReturn(id);
        when(catalog.getName()).thenReturn(name);
        when(catalog.isMarketplaceExportEnabled("Allegro")).thenReturn(exportsToAllegro);
        return catalog;
    }
}
