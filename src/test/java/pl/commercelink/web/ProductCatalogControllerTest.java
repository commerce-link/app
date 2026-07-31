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
import pl.commercelink.products.CategoryOption;
import pl.commercelink.products.CategorySelection;
import pl.commercelink.products.PimCategoryOptions;
import pl.commercelink.products.PriceDefinition;
import pl.commercelink.products.Product;
import pl.commercelink.products.ProductCatalog;
import pl.commercelink.products.ProductCatalogRepository;
import pl.commercelink.products.ProductRepository;
import pl.commercelink.products.StockDefinition;
import pl.commercelink.starter.security.model.CustomUser;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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

    @Mock
    private PimCategoryOptions pimCategoryOptions;

    @Mock
    private StoresRepository storesRepository;

    @Mock
    private Store store;

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
    void keepsProductsWhenAnotherDefinitionSharesAtLeastOneCategory() {
        // given
        CategoryDefinition removed = definitionWithCategories(CategoryDefinitionType.Dynamic, "194", "195");
        CategoryDefinition surviving = definitionWithCategories(CategoryDefinitionType.Dynamic, "195", "989");
        when(catalog.removeCategoryDefinition(removed.getCategoryId())).thenReturn(removed);
        when(catalog.getCategories()).thenReturn(List.of(surviving));

        // when
        controller.deleteCategoryDefinition(CATALOG_ID, removed.getCategoryId());

        // then
        verify(productRepository, never()).delete(anyList());
    }

    @Test
    void deletesProductsWhenNoOtherDefinitionSharesAnyCategory() {
        // given
        CategoryDefinition removed = definitionWithCategories(CategoryDefinitionType.Dynamic, "194");
        CategoryDefinition surviving = definitionWithCategories(CategoryDefinitionType.Dynamic, "989");
        when(catalog.removeCategoryDefinition(removed.getCategoryId())).thenReturn(removed);
        when(catalog.getCategories()).thenReturn(List.of(surviving));
        when(productRepository.findAll(removed.getCategoryId())).thenReturn(List.of(new Product(removed.getCategoryId())));

        // when
        controller.deleteCategoryDefinition(CATALOG_ID, removed.getCategoryId());

        // then
        verify(productRepository).delete(anyList());
    }

    @Test
    void deletesProductsWhenRemovedDefinitionHadNoCategories() {
        // given
        CategoryDefinition removed = definitionWithCategories(CategoryDefinitionType.Dynamic);
        CategoryDefinition surviving = definitionWithCategories(CategoryDefinitionType.Dynamic, "989");
        when(catalog.removeCategoryDefinition(removed.getCategoryId())).thenReturn(removed);
        when(catalog.getCategories()).thenReturn(List.of(surviving));
        when(productRepository.findAll(removed.getCategoryId())).thenReturn(List.of(new Product(removed.getCategoryId())));

        // when
        controller.deleteCategoryDefinition(CATALOG_ID, removed.getCategoryId());

        // then
        verify(productRepository).delete(anyList());
    }

    @Test
    void warnsWhenDynamicDefinitionIsSavedWithCategoryThatHasNoInventory() {
        // given
        CategoryDefinition definition = definitionWithCategories(CategoryDefinitionType.Dynamic, "195");
        CategorySelection myszki = CategorySelection.of(List.of("195"), List.of("Myszki"));
        when(pimCategoryOptions.selectionOf(List.of("195"))).thenReturn(myszki);
        when(inventoryView.findAllByProductCategories(myszki)).thenReturn(List.of());
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        // when
        controller.saveCategoryDefinition(CATALOG_ID, definition, new ExtendedModelMap(), redirectAttributes);

        // then
        assertThat(redirectAttributes.getFlashAttributes()).containsKey("warningMessage");
    }

    @Test
    void doesNotWarnWhenDynamicDefinitionCategoryResolvesToInventoryWithOffers() {
        // given
        CategoryDefinition definition = definitionWithCategories(CategoryDefinitionType.Dynamic, "194");
        CategorySelection klawiatury = CategorySelection.of(List.of("194"), List.of("Klawiatury"));
        when(pimCategoryOptions.selectionOf(List.of("194"))).thenReturn(klawiatury);
        when(matchedInventory.hasAnyOffers()).thenReturn(true);
        when(inventoryView.findAllByProductCategories(klawiatury)).thenReturn(List.of(matchedInventory));
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        // when
        controller.saveCategoryDefinition(CATALOG_ID, definition, new ExtendedModelMap(), redirectAttributes);

        // then
        assertThat(redirectAttributes.getFlashAttributes()).doesNotContainKey("warningMessage");
    }

    @Test
    void doesNotWarnForManagedDefinitionBecauseItsProductsDoNotComeFromTheCategory() {
        // given
        CategoryDefinition definition = definitionWithCategories(CategoryDefinitionType.Managed, "195");
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        // when
        controller.saveCategoryDefinition(CATALOG_ID, definition, new ExtendedModelMap(), redirectAttributes);

        // then
        verify(inventoryView, never()).findAllByProductCategories(any());
        assertThat(redirectAttributes.getFlashAttributes()).doesNotContainKey("warningMessage");
    }

    @Test
    void warnsWhenDynamicDefinitionCategoryHasInventoryEntriesButNoneOfThemHasOffers() {
        // given
        CategoryDefinition definition = definitionWithCategories(CategoryDefinitionType.Dynamic, "989");
        CategorySelection procesory = CategorySelection.of(List.of("989"), List.of("Procesory"));
        when(pimCategoryOptions.selectionOf(List.of("989"))).thenReturn(procesory);
        when(matchedInventory.hasAnyOffers()).thenReturn(false);
        when(inventoryView.findAllByProductCategories(procesory)).thenReturn(List.of(matchedInventory));
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        // when
        controller.saveCategoryDefinition(CATALOG_ID, definition, new ExtendedModelMap(), redirectAttributes);

        // then
        assertThat(redirectAttributes.getFlashAttributes()).containsKey("warningMessage");
    }

    @Test
    void warnsListingOnlyTheSelectedCategoriesWithoutOffers() {
        // given
        CategoryDefinition definition = definitionWithCategories(CategoryDefinitionType.Dynamic, "194", "195");
        CategorySelection klawiatury = CategorySelection.of(List.of("194"), List.of("Klawiatury"));
        CategorySelection myszki = CategorySelection.of(List.of("195"), List.of("Myszki"));
        when(pimCategoryOptions.selectionOf(List.of("194"))).thenReturn(klawiatury);
        when(pimCategoryOptions.selectionOf(List.of("195"))).thenReturn(myszki);
        when(matchedInventory.hasAnyOffers()).thenReturn(true);
        when(inventoryView.findAllByProductCategories(klawiatury)).thenReturn(List.of(matchedInventory));
        when(inventoryView.findAllByProductCategories(myszki)).thenReturn(List.of());
        when(pimCategoryOptions.joinedNamesOf(List.of("195"))).thenReturn("Myszki");
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        // when
        controller.saveCategoryDefinition(CATALOG_ID, definition, new ExtendedModelMap(), redirectAttributes);

        // then
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(messageSource).getMessage(eq("catalog.category.emptyInventory"), args.capture(), any(Locale.class));
        assertThat(args.getValue()).containsExactly("Myszki");
    }

    @Test
    void doesNotWarnWhenEverySelectedCategoryHasOffers() {
        // given
        CategoryDefinition definition = definitionWithCategories(CategoryDefinitionType.Dynamic, "194", "195");
        CategorySelection klawiatury = CategorySelection.of(List.of("194"), List.of("Klawiatury"));
        CategorySelection myszki = CategorySelection.of(List.of("195"), List.of("Myszki"));
        when(pimCategoryOptions.selectionOf(List.of("194"))).thenReturn(klawiatury);
        when(pimCategoryOptions.selectionOf(List.of("195"))).thenReturn(myszki);
        when(matchedInventory.hasAnyOffers()).thenReturn(true);
        when(inventoryView.findAllByProductCategories(klawiatury)).thenReturn(List.of(matchedInventory));
        when(inventoryView.findAllByProductCategories(myszki)).thenReturn(List.of(matchedInventory));
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        // when
        controller.saveCategoryDefinition(CATALOG_ID, definition, new ExtendedModelMap(), redirectAttributes);

        // then
        assertThat(redirectAttributes.getFlashAttributes()).doesNotContainKey("warningMessage");
    }

    @Test
    void savingDefinitionWithBlankCategoryIsStillPersisted() {
        // given
        CategoryDefinition blankCategoryDefinition = definition(CategoryDefinitionType.Managed, "");
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        // when
        controller.saveCategoryDefinition(CATALOG_ID, blankCategoryDefinition, new ExtendedModelMap(), redirectAttributes);

        // then
        verify(catalog).addOrUpdateCategoryDefinition(blankCategoryDefinition);
    }

    @Test
    void savingDefinitionPersistsSelectedCategories() {
        // given
        CategoryDefinition posted = definitionWithCategories(CategoryDefinitionType.Managed, "1234", "5678");
        ArgumentCaptor<CategoryDefinition> captor = ArgumentCaptor.forClass(CategoryDefinition.class);

        // when
        controller.saveCategoryDefinition(CATALOG_ID, posted, new ExtendedModelMap(), new RedirectAttributesModelMap());

        // then
        verify(catalog).addOrUpdateCategoryDefinition(captor.capture());
        assertThat(captor.getValue().getCategories()).containsExactly("1234", "5678");
    }

    @Test
    void editingCategoryPopulatesFormOptionsFromIds() throws Exception {
        // given
        CategoryDefinition definition = definitionWithCategories(CategoryDefinitionType.Dynamic, "194", "195");
        when(catalog.findCategoryDefinition(definition.getCategoryId())).thenReturn(definition);
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        when(store.getEnabledCategories()).thenReturn(List.of("Komputery i urządzenia peryferyjne"));
        when(store.getMarketplaces()).thenReturn(List.of());
        List<CategoryOption> options = List.of(new CategoryOption("194", "Klawiatury"), new CategoryOption("195", "Myszki"));
        when(pimCategoryOptions.categoryOptionsById(List.of("Komputery i urządzenia peryferyjne"), List.of("194", "195")))
                .thenReturn(options);
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        controller.editCategory(CATALOG_ID, definition.getCategoryId(), model);

        // then
        assertThat(model.getAttribute("productCategories")).isEqualTo(options);
    }

    @Test
    void savingDynamicDefinitionWithoutMappingWarnsWithoutQueryingInventory() {
        // given
        CategoryDefinition unmappedDefinition = definition(CategoryDefinitionType.Dynamic, "");
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        // when
        controller.saveCategoryDefinition(CATALOG_ID, unmappedDefinition, new ExtendedModelMap(), redirectAttributes);

        // then
        verify(inventoryView, never()).findAllByProductCategories(any());
        verify(messageSource).getMessage(eq("catalog.category.noMapping"), isNull(), any(Locale.class));
        assertThat(redirectAttributes.getFlashAttributes()).containsKey("warningMessage");
    }

    @Test
    void savingProductWithServiceCheckboxMarksItAsService() {
        // given
        CategoryDefinition definition = definition(CategoryDefinitionType.Managed, null);
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

    private CategoryDefinition definitionWithCategories(CategoryDefinitionType type, String... categoryIds) {
        CategoryDefinition definition = definition(type, null);
        definition.setCategories(List.of(categoryIds));
        return definition;
    }

    private void authenticateAsStoreAdmin() {
        CustomUser user = new CustomUser(null, null, Map.of("storeId", STORE_ID, "role", "ADMIN"));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")))
        );
    }
}
