package pl.commercelink.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import pl.commercelink.products.AvailabilityDefinition;
import pl.commercelink.products.CategoryDefinition;
import pl.commercelink.products.CategoryDefinitionType;
import pl.commercelink.products.PriceDefinition;
import pl.commercelink.products.ProductCatalog;
import pl.commercelink.products.ProductCatalogRepository;
import pl.commercelink.products.StockDefinition;
import pl.commercelink.starter.security.model.CustomUser;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProductCatalogControllerTest {

    private static final String STORE_ID = "store-1";
    private static final String CATALOG_ID = "catalog-1";

    @Mock
    private ProductCatalogRepository productCatalogRepository;

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
