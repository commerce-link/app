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
import pl.commercelink.pricelist.PricelistFinder;
import pl.commercelink.products.CategoryDefinition;
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
    void listsEveryCatalogProductWithAPimIdIncludingThoseMissingFromThePricelist() {
        // given
        ProductCatalog catalog = catalog();
        when(productCatalogRepository.findById(STORE_ID, CATALOG_ID)).thenReturn(catalog);
        when(productRepository.findAll(catalog)).thenReturn(List.of(
                product("pim-kettle", "Czajnik", "DMW-CZ17"),
                product("pim-flask", "Termos", "DMW-TS10"),
                product(null, "Bez karty", "DMW-X1")));
        when(pricelistFinder.availabilityAndPrices(STORE_ID, CATALOG_ID)).thenReturn(List.of(
                new AvailabilityAndPrice("pim-kettle", "2001", "DMW-CZ17", "Domowik", "Domowik", "Czajnik",
                        "Dom i kuchnia", 129, 5, 2, 129, false)));

        // when
        List<LinkOfferProduct> products = controller.products(CATALOG_ID);

        // then
        assertThat(products).containsExactly(
                new LinkOfferProduct("pim-kettle", "Czajnik", "Domowik", "DMW-CZ17", "Dom i kuchnia", 129L),
                new LinkOfferProduct("pim-flask", "Termos", "Domowik", "DMW-TS10", "Dom i kuchnia", null));
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

    private static Product product(String pimId, String name, String mfn) {
        return new Product("cat-home", pimId, "2001", mfn, "Domowik", "Domowik", name, "Default");
    }
}
