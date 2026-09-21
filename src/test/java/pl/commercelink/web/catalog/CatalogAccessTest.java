package pl.commercelink.web.catalog;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import pl.commercelink.products.CategoryDefinition;
import pl.commercelink.products.Product;
import pl.commercelink.products.ProductCatalog;
import pl.commercelink.products.ProductCatalogRepository;
import pl.commercelink.products.ProductRepository;
import pl.commercelink.stores.MarketplaceIntegration;
import pl.commercelink.stores.Store;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CatalogAccessTest {

    @Mock
    private ProductCatalogRepository catalogs;
    @Mock
    private ProductRepository products;
    @InjectMocks
    private CatalogAccess access;

    @Test
    void missingCatalogIsNotFound() {
        // given
        when(catalogs.findById("store", "nope")).thenReturn(null);

        // when / then
        assertThatThrownBy(() -> access.requireCatalog("store", "nope"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void missingCategoryIsNotFoundInsteadOfIllegalArgument() {
        // given
        ProductCatalog catalog = new ProductCatalog("store", "Parts");

        // when / then
        assertThatThrownBy(() -> access.requireCategory(catalog, "missing"))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void productMustBelongToTheCategory() {
        // given
        CategoryDefinition category = new CategoryDefinition().withName("GPU").withGeneratedId();
        when(products.findByProductId(category.getCategoryId(), "p1")).thenReturn(null);

        // when / then
        assertThatThrownBy(() -> access.requireProduct(category, "p1")).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void marketplaceMustBeConnectedToTheStore() {
        // given
        Store store = mock(Store.class);
        MarketplaceIntegration allegro = mock(MarketplaceIntegration.class);
        when(allegro.getName()).thenReturn("Allegro");
        when(store.getMarketplaces()).thenReturn(List.of(allegro));

        // when / then
        assertThat(access.requireMarketplace(store, "Allegro")).isEqualTo("Allegro");
        assertThatThrownBy(() -> access.requireMarketplace(store, "Empik")).isInstanceOf(ResponseStatusException.class);
    }
}
