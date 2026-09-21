package pl.commercelink.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.MessageSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.ui.ExtendedModelMap;
import pl.commercelink.pim.api.PimCatalog;
import pl.commercelink.products.AvailabilityDefinition;
import pl.commercelink.products.CategoryDefinition;
import pl.commercelink.products.CategoryDefinitionType;
import pl.commercelink.products.PimCategoryOptions;
import pl.commercelink.products.PriceDefinition;
import pl.commercelink.products.Product;
import pl.commercelink.products.ProductCatalog;
import pl.commercelink.products.ProductCatalogRepository;
import pl.commercelink.products.ProductRepository;
import pl.commercelink.products.StockDefinition;
import pl.commercelink.starter.security.model.CustomUser;
import pl.commercelink.stores.StoresRepository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProductCatalogControllerTest {

    private static final String STORE_ID = "store-1";
    private static final String CATALOG_ID = "catalog-1";

    @Mock
    private ProductCatalogRepository productCatalogRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private MessageSource messageSource;

    @Mock
    private ProductCatalog catalog;

    @Mock
    private PimCatalog pimCatalog;

    @Mock
    private PimCategoryOptions pimCategoryOptions;

    @Mock
    private StoresRepository storesRepository;

    @InjectMocks
    private ProductCatalogController controller;

    @BeforeEach
    void setUp() {
        authenticateAsStoreAdmin();
        when(productCatalogRepository.findById(STORE_ID, CATALOG_ID)).thenReturn(catalog);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void savingProductWithServiceCheckboxMarksItAsService() {
        // given
        CategoryDefinition definition = definition(CategoryDefinitionType.Managed, (String) null);
        when(catalog.findCategoryDefinition(definition.getCategoryId())).thenReturn(definition);
        when(productRepository.findByProductId(definition.getCategoryId(), "prod-1")).thenReturn(null);
        when(pimCatalog.findByGtinOrMpn(any(), any())).thenReturn(Optional.empty());
        Product product = new Product(definition.getCategoryId());
        product.setProductId("prod-1");
        product.setName("Montaż PC");
        product.setService(true);

        // when
        controller.saveProduct(CATALOG_ID, definition.getCategoryId(), "prod-1", product, new ExtendedModelMap());

        // then
        ArgumentCaptor<Product> productCaptor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(productCaptor.capture());
        assertThat(productCaptor.getValue().isService()).isTrue();
    }

    @Test
    void savingProductWithoutServiceCheckboxLeavesItAsProduct() {
        // given
        CategoryDefinition definition = definition(CategoryDefinitionType.Managed, "Karty graficzne");
        when(catalog.findCategoryDefinition(definition.getCategoryId())).thenReturn(definition);
        when(productRepository.findByProductId(definition.getCategoryId(), "prod-1")).thenReturn(null);
        when(pimCatalog.findByGtinOrMpn(any(), any())).thenReturn(Optional.empty());
        Product product = new Product(definition.getCategoryId());
        product.setProductId("prod-1");
        product.setName("Karta graficzna");
        product.setService(false);

        // when
        controller.saveProduct(CATALOG_ID, definition.getCategoryId(), "prod-1", product, new ExtendedModelMap());

        // then
        ArgumentCaptor<Product> productCaptor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(productCaptor.capture());
        assertThat(productCaptor.getValue().isService()).isFalse();
    }

    @Test
    void editingProductCanClearTheServiceFlag() {
        // given
        CategoryDefinition definition = definition(CategoryDefinitionType.Managed, "Karty graficzne");
        when(catalog.findCategoryDefinition(definition.getCategoryId())).thenReturn(definition);
        Product existingProduct = new Product(definition.getCategoryId());
        existingProduct.setProductId("prod-1");
        existingProduct.setName("Montaż PC");
        existingProduct.setService(true);
        when(productRepository.findByProductId(definition.getCategoryId(), "prod-1")).thenReturn(existingProduct);
        Product product = new Product(definition.getCategoryId());
        product.setProductId("prod-1");
        product.setName("Montaż PC");
        product.setService(false);

        // when
        controller.saveProduct(CATALOG_ID, definition.getCategoryId(), "prod-1", product, new ExtendedModelMap());

        // then
        ArgumentCaptor<Product> productCaptor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(productCaptor.capture());
        assertThat(productCaptor.getValue().isService()).isFalse();
    }

    private CategoryDefinition definition(CategoryDefinitionType type, String category) {
        CategoryDefinition definition = new CategoryDefinition().withGeneratedId();
        definition.setName("Pozycja");
        definition.setType(type);
        definition.setCategory(category);
        definition.setStockDefinition(new StockDefinition(2, 5, 20));
        definition.setAvailabilityDefinition(new AvailabilityDefinition(1, 2));
        definition.setPriceDefinitions(List.of(
                new PriceDefinition(1.2, 100, 0, 0, 0, PriceDefinition.DEFAULT_PRICING_GROUP)));
        return definition;
    }

    private void authenticateAsStoreAdmin() {
        CustomUser user = new CustomUser(null, null, Map.of("storeId", STORE_ID, "role", "ADMIN"));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")))
        );
    }
}
