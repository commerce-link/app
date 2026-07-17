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
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import pl.commercelink.inventory.Inventory;
import pl.commercelink.inventory.InventoryView;
import pl.commercelink.inventory.MatchedInventory;
import pl.commercelink.pim.api.PimCatalog;
import pl.commercelink.products.AvailabilityDefinition;
import pl.commercelink.products.CategoryDefinition;
import pl.commercelink.products.CategoryDefinitionType;
import pl.commercelink.products.PriceDefinition;
import pl.commercelink.products.Product;
import pl.commercelink.products.ProductCatalog;
import pl.commercelink.products.ProductCatalogRepository;
import pl.commercelink.products.ProductRepository;
import pl.commercelink.products.StockDefinition;
import pl.commercelink.starter.security.model.CustomUser;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
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
    private Inventory inventory;

    @Mock
    private InventoryView inventoryView;

    @Mock
    private MessageSource messageSource;

    @Mock
    private ProductCatalog catalog;

    @Mock
    private MatchedInventory matchedInventory;

    @Mock
    private PimCatalog pimCatalog;

    @InjectMocks
    private ProductCatalogController controller;

    @BeforeEach
    void setUp() {
        authenticateAsStoreAdmin();
        when(productCatalogRepository.findById(STORE_ID, CATALOG_ID)).thenReturn(catalog);
        when(inventory.withEnabledSuppliersOnly(STORE_ID)).thenReturn(inventoryView);
        when(messageSource.getMessage(any(String.class), any(), any(Locale.class))).thenReturn("brak produktow");
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void keepsProductsWhenAnotherDefinitionResolvesToTheSameInventoryCategory() {
        // given
        CategoryDefinition removed = definition(CategoryDefinitionType.Dynamic, "Karty graficzne");
        CategoryDefinition remaining = definition(CategoryDefinitionType.Dynamic, "GPU");
        when(catalog.removeCategoryDefinition(removed.getCategoryId())).thenReturn(removed);
        when(catalog.getCategories()).thenReturn(List.of(remaining));

        // when
        controller.deleteCategoryDefinition(CATALOG_ID, removed.getCategoryId());

        // then
        verify(productRepository, never()).delete(anyList());
    }

    @Test
    void warnsWhenDynamicDefinitionIsSavedWithCategoryThatHasNoInventory() {
        // given
        when(inventoryView.findAllByProductCategory("Kołdry")).thenReturn(List.of());
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        // when
        controller.saveCategoryDefinition(CATALOG_ID, definition(CategoryDefinitionType.Dynamic, "Kołdry"), new ExtendedModelMap(), redirectAttributes);

        // then
        assertThat(redirectAttributes.getFlashAttributes()).containsKey("warningMessage");
    }

    @Test
    void doesNotWarnWhenDynamicDefinitionCategoryResolvesToInventoryWithOffers() {
        // given
        when(matchedInventory.hasAnyOffers()).thenReturn(true);
        when(inventoryView.findAllByProductCategory("GPU")).thenReturn(List.of(matchedInventory));
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        // when
        controller.saveCategoryDefinition(CATALOG_ID, definition(CategoryDefinitionType.Dynamic, "Karty graficzne"), new ExtendedModelMap(), redirectAttributes);

        // then
        assertThat(redirectAttributes.getFlashAttributes()).doesNotContainKey("warningMessage");
    }

    @Test
    void doesNotWarnForManagedDefinitionBecauseItsProductsDoNotComeFromTheCategory() {
        // given
        when(inventoryView.findAllByProductCategory(any())).thenReturn(List.of());
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        // when
        controller.saveCategoryDefinition(CATALOG_ID, definition(CategoryDefinitionType.Managed, "Kołdry"), new ExtendedModelMap(), redirectAttributes);

        // then
        assertThat(redirectAttributes.getFlashAttributes()).doesNotContainKey("warningMessage");
    }

    @Test
    void warnsWhenDynamicDefinitionCategoryHasInventoryEntriesButNoneOfThemHasOffers() {
        // given
        when(matchedInventory.hasAnyOffers()).thenReturn(false);
        when(inventoryView.findAllByProductCategory("GPU")).thenReturn(List.of(matchedInventory));
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        // when
        controller.saveCategoryDefinition(CATALOG_ID, definition(CategoryDefinitionType.Dynamic, "Karty graficzne"), new ExtendedModelMap(), redirectAttributes);

        // then
        assertThat(redirectAttributes.getFlashAttributes()).containsKey("warningMessage");
    }

    @Test
    void savingServiceDefinitionNormalizesBlankCategoryToNull() {
        // given
        CategoryDefinition serviceDefinition = definition(CategoryDefinitionType.Managed, "");
        serviceDefinition.setService(true);
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        // when
        controller.saveCategoryDefinition(CATALOG_ID, serviceDefinition, new ExtendedModelMap(), redirectAttributes);

        // then
        verify(catalog).addOrUpdateCategoryDefinition(serviceDefinition);
        assertThat(serviceDefinition.getCategory()).isNull();
    }

    @Test
    void savingDynamicServiceDefinitionDoesNotQueryInventoryForAWarning() {
        // given
        CategoryDefinition serviceDefinition = definition(CategoryDefinitionType.Dynamic, "");
        serviceDefinition.setService(true);
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        // when
        controller.saveCategoryDefinition(CATALOG_ID, serviceDefinition, new ExtendedModelMap(), redirectAttributes);

        // then
        verify(inventoryView, never()).findAllByProductCategory(any());
        assertThat(redirectAttributes.getFlashAttributes()).doesNotContainKey("warningMessage");
    }

    @Test
    void savingNewProductUnderServiceDefinitionMarksItAsService() {
        // given
        CategoryDefinition serviceDefinition = definition(CategoryDefinitionType.Managed, null);
        serviceDefinition.setService(true);
        when(catalog.findCategoryDefinition(serviceDefinition.getCategoryId())).thenReturn(serviceDefinition);
        when(productRepository.findByProductId(serviceDefinition.getCategoryId(), "prod-1")).thenReturn(null);
        when(pimCatalog.findByGtinOrMpn(any(), any())).thenReturn(Optional.empty());
        Product product = new Product(serviceDefinition.getCategoryId());
        product.setProductId("prod-1");
        product.setName("Montaż PC");

        // when
        controller.saveProduct(CATALOG_ID, serviceDefinition.getCategoryId(), "prod-1", product, new ExtendedModelMap());

        // then
        ArgumentCaptor<Product> productCaptor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(productCaptor.capture());
        assertThat(productCaptor.getValue().isService()).isTrue();
    }

    @Test
    void savingNewProductUnderRegularDefinitionLeavesItAsProduct() {
        // given
        CategoryDefinition regularDefinition = definition(CategoryDefinitionType.Managed, "Karty graficzne");
        when(catalog.findCategoryDefinition(regularDefinition.getCategoryId())).thenReturn(regularDefinition);
        when(productRepository.findByProductId(regularDefinition.getCategoryId(), "prod-1")).thenReturn(null);
        when(pimCatalog.findByGtinOrMpn(any(), any())).thenReturn(Optional.empty());
        Product product = new Product(regularDefinition.getCategoryId());
        product.setProductId("prod-1");
        product.setName("Karta graficzna");

        // when
        controller.saveProduct(CATALOG_ID, regularDefinition.getCategoryId(), "prod-1", product, new ExtendedModelMap());

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
