package pl.commercelink.products;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.commercelink.pim.api.PimCatalog;
import pl.commercelink.products.information.BrandFacade;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProductBrandTest {

    @BeforeEach
    void setUp() {
        PimCatalog stub = mock(PimCatalog.class);
        when(stub.unifyBrand("Asustek")).thenReturn("Asus");
        when(stub.unifyBrand("asustek")).thenReturn("Asus");
        when(stub.unifyBrand("Asus")).thenReturn("Asus");
        when(stub.unifyBrand("HYPERX")).thenReturn("HP");
        when(stub.unifyBrand("HP")).thenReturn("HP");
        when(stub.unifyBrand("RandomBrand")).thenReturn("RandomBrand");
        when(stub.unifyBrand(null)).thenReturn(null);
        BrandFacade.initialize(stub);
    }

    @AfterEach
    void tearDown() {
        BrandFacade.initialize(null);
    }

    @Test
    void setBrandCanonicalizesAlias() {
        Product product = new Product();
        product.setBrand("Asustek");

        assertThat(product.getBrand()).isEqualTo("Asus");
    }

    @Test
    void setBrandCaseInsensitive() {
        Product product = new Product();
        product.setBrand("asustek");

        assertThat(product.getBrand()).isEqualTo("Asus");
    }

    @Test
    void setBrandPassesThroughForUnknownBrand() {
        Product product = new Product();
        product.setBrand("RandomBrand");

        assertThat(product.getBrand()).isEqualTo("RandomBrand");
    }

    @Test
    void setBrandNullStaysNull() {
        Product product = new Product();
        product.setBrand(null);

        assertThat(product.getBrand()).isNull();
    }

    @Test
    void getBrandCanonicalizesStaleData() throws Exception {
        Product product = new Product();
        Field brandField = Product.class.getDeclaredField("brand");
        brandField.setAccessible(true);
        brandField.set(product, "HYPERX");

        assertThat(product.getBrand()).isEqualTo("HP");
    }
}
