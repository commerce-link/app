package pl.commercelink.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.pricelist.AvailabilityAndPrice;
import pl.commercelink.pricelist.Pricelist;
import pl.commercelink.pricelist.PricelistFinder;
import pl.commercelink.products.CategoryDefinition;
import pl.commercelink.products.CategoryDefinitionType;
import pl.commercelink.products.Product;
import pl.commercelink.products.ProductCatalog;
import pl.commercelink.products.ProductCatalogRepository;
import pl.commercelink.products.ProductRepository;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.web.orders.LinkOfferProduct;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LinkOfferProductsControllerTest {

    private static final String STORE_ID = "store-1";
    private static final String CATALOG_ID = "cat-1";

    @Mock
    private ProductCatalogRepository productCatalogRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private PricelistFinder pricelistFinder;

    @InjectMocks
    private LinkOfferProductsController controller;

    private MockedStatic<CustomSecurityContext> securityStub;

    @BeforeEach
    void setUp() {
        securityStub = mockStatic(CustomSecurityContext.class);
        securityStub.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);
    }

    @AfterEach
    void tearDown() {
        securityStub.close();
    }

    @Test
    void listsEveryCatalogProductWithAnEanIncludingThoseWithoutACodeOrMissingFromThePricelist() {
        // given
        ProductCatalog catalog = catalog();
        when(productCatalogRepository.findById(STORE_ID, CATALOG_ID)).thenReturn(catalog);
        when(productRepository.findAll(catalog)).thenReturn(List.of(
                product("p1", "pim-kettle", "5900000000011", "Czajnik", "DMW-CZ17"),
                product("p2", "pim-flask", "5900000000028", "Termos", "DMW-TS10"),
                product("p3", null, "5900000000035", "Bez karty", "DMW-X1"),
                product("p4", "pim-no-ean", null, "Bez EAN", "DMW-X2"),
                product("p5", "pim-no-mfn", "5900000000042", "Bez kodu", " ")));
        when(pricelistFinder.newestPricelist(STORE_ID, CATALOG_ID)).thenReturn(new Pricelist("p1", List.of(
                new AvailabilityAndPrice("pim-kettle", "5900000000011", "DMW-CZ17", "Domowik", "Domowik", "Czajnik",
                        "Dom i kuchnia", 129, 5, 2, 129, false))));

        // when
        List<LinkOfferProduct> products = controller.products(CATALOG_ID);

        // then
        assertThat(products).containsExactly(
                new LinkOfferProduct("cat-home", "p3", "5900000000035", "Bez karty", "Domowik", "DMW-X1", "Dom i kuchnia", null),
                new LinkOfferProduct("cat-home", "p5", "5900000000042", "Bez kodu", "Domowik", "", "Dom i kuchnia", null),
                new LinkOfferProduct("cat-home", "p1", "5900000000011", "Czajnik", "Domowik", "DMW-CZ17", "Dom i kuchnia", 129L),
                new LinkOfferProduct("cat-home", "p2", "5900000000028", "Termos", "Domowik", "DMW-TS10", "Dom i kuchnia", null));
    }

    @Test
    void aProductWithoutAPimIdTakesThePriceOfThePricelistRowWithItsEan() {
        // given
        ProductCatalog catalog = catalog();
        when(productCatalogRepository.findById(STORE_ID, CATALOG_ID)).thenReturn(catalog);
        when(productRepository.findAll(catalog)).thenReturn(List.of(
                product("p1", null, "5900000000011", "Czajnik", "DMW-CZ17")));
        when(pricelistFinder.newestPricelist(STORE_ID, CATALOG_ID)).thenReturn(new Pricelist("p1", List.of(
                new AvailabilityAndPrice(null, "05900000000011", "DMW-CZ17", "Domowik", "Domowik", "Czajnik",
                        "Dom i kuchnia", 149, 5, 2, 149, false))));

        // when
        List<LinkOfferProduct> products = controller.products(CATALOG_ID);

        // then
        assertThat(products).extracting(LinkOfferProduct::price).containsExactly(149L);
    }

    @Test
    void leavesOutAProductWhoseEanHasNotTheFormatOfOne() {
        // given
        ProductCatalog catalog = catalog();
        when(productCatalogRepository.findById(STORE_ID, CATALOG_ID)).thenReturn(catalog);
        when(productRepository.findAll(catalog)).thenReturn(List.of(
                product("p1", null, "2001000000012", "Czajnik", "DMW-CZ17"),
                product("p2", null, "brak kodu", "Kubek", "DMW-KB1"),
                product("p3", null, "2001000000029:2001000000036", "Lampka", "DMW-LN1")));
        when(pricelistFinder.newestPricelist(STORE_ID, CATALOG_ID)).thenReturn(Pricelist.empty());

        // when
        List<LinkOfferProduct> products = controller.products(CATALOG_ID);

        // then
        assertThat(products).extracting(LinkOfferProduct::productId).containsExactly("p1");
    }

    @Test
    void leavesOutTheProductsLeftInAnAutomaticCategory() {
        // given: an automatic category keeps the products of its manual days only for a return to manual
        ProductCatalog catalog = catalog();
        CategoryDefinition automatic = new CategoryDefinition();
        automatic.setCategoryId("cat-auto");
        automatic.setName("Automatyczna");
        automatic.setType(CategoryDefinitionType.Dynamic);
        catalog.setCategories(List.of(catalog.getCategories().get(0), automatic));
        Product leftOver = new Product("cat-auto", "pim-left", "5900000000059", "DMW-L1", "Domowik", "Domowik", "Zostawiony", "Default");
        leftOver.setProductId("p9");
        when(productCatalogRepository.findById(STORE_ID, CATALOG_ID)).thenReturn(catalog);
        when(productRepository.findAll(catalog)).thenReturn(List.of(
                product("p1", "pim-kettle", "5900000000011", "Czajnik", "DMW-CZ17"), leftOver));
        when(pricelistFinder.newestPricelist(STORE_ID, CATALOG_ID)).thenReturn(new Pricelist("p1", List.of()));

        // when
        List<LinkOfferProduct> products = controller.products(CATALOG_ID);

        // then
        assertThat(products).extracting(LinkOfferProduct::productId).containsExactly("p1");
    }

    @Test
    void anUnknownCatalogOfTheStoreListsNothing() {
        // given
        when(productCatalogRepository.findById(STORE_ID, "other")).thenReturn(null);

        // when
        List<LinkOfferProduct> products = controller.products("other");

        // then
        assertThat(products).isEmpty();
        verify(productRepository, never()).findAll(any(ProductCatalog.class));
    }

    private static ProductCatalog catalog() {
        CategoryDefinition home = new CategoryDefinition();
        home.setCategoryId("cat-home");
        home.setName("Dom i kuchnia");
        ProductCatalog catalog = new ProductCatalog(STORE_ID, "Katalog");
        catalog.setCategories(List.of(home));
        return catalog;
    }

    private static Product product(String productId, String pimId, String ean, String name, String mfn) {
        Product product = new Product("cat-home", pimId, ean, mfn, "Domowik", "Domowik", name, "Default");
        product.setProductId(productId);
        return product;
    }
}
