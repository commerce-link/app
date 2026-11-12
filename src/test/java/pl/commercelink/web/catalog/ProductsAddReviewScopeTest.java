package pl.commercelink.web.catalog;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.MessageSource;
import pl.commercelink.inventory.Inventory;
import pl.commercelink.inventory.InventoryKey;
import pl.commercelink.inventory.InventoryView;
import pl.commercelink.inventory.MatchedInventory;
import pl.commercelink.invoicing.api.Price;
import pl.commercelink.pim.ScopedPimCatalog;
import pl.commercelink.pim.api.PimCatalog;
import pl.commercelink.pim.api.PimEntry;
import pl.commercelink.pim.api.PimIdentifier;
import pl.commercelink.pim.api.PimIdentifierType;
import pl.commercelink.products.CatalogPlacement;
import pl.commercelink.products.CategoryDefinition;
import pl.commercelink.products.ManagedCategoryProducts;
import pl.commercelink.products.Product;
import pl.commercelink.products.ProductRepository;
import pl.commercelink.products.brand.BrandMapper;
import pl.commercelink.products.information.PimEntryRequester;
import pl.commercelink.products.information.StorePimAliases;
import pl.commercelink.taxonomy.Taxonomy;
import pl.commercelink.web.dtos.ProductsBulkAddForm;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProductsAddReviewScopeTest {

    private static final String EAN = ScopedPimCatalog.STORE_GTIN;
    private static final String MFN = "M-591";
    private static final String EAN_UNKNOWN_TO_PIM = "5900000000999";

    @Mock private ProductRepository productRepository;
    @Mock private Inventory inventory;
    @Mock private InventoryView view;
    @Mock private PimCatalog brandList;
    @Mock private MessageSource messageSource;
    @Mock private CatalogPlacement catalogPlacement;
    @Mock private PimEntryRequester pimEntryRequester;

    private ProductsAddReview review(PimCatalog catalog) {
        BrandMapper brandMapper = new BrandMapper(brandList);
        when(inventory.withEnabledSuppliersOnly(anyString())).thenReturn(view);
        when(productRepository.findAll(anyString())).thenReturn(List.of());
        MatchedInventory notInInventory = mock(MatchedInventory.class);
        when(notInInventory.isEmpty()).thenReturn(true);
        when(view.findByEan(anyString())).thenReturn(notInInventory);
        MatchedInventory matched = mock(MatchedInventory.class);
        when(matched.isEmpty()).thenReturn(false);
        when(matched.getInventoryKey()).thenReturn(new InventoryKey(EAN, MFN));
        when(matched.getTaxonomy()).thenReturn(new Taxonomy(EAN, MFN, "Brand", "Taxonomy name", "GPU", 1, null, null));
        when(matched.getLowestPrice()).thenReturn(Price.fromGross(1000));
        when(view.findByEan(EAN)).thenReturn(matched);
        StorePimAliases aliases = new StorePimAliases(catalog);
        return new ProductsAddReview(productRepository, inventory, catalog, brandMapper, messageSource, aliases,
                new ManagedCategoryProducts(productRepository, catalogPlacement, aliases, pimEntryRequester));
    }

    private static PimCatalog catalog() {
        return ScopedPimCatalog.of(
                ScopedPimCatalog.globalEntry("g1", ScopedPimCatalog.GLOBAL_GTIN, "M-590", "GPU"),
                ScopedPimCatalog.storeEntry("s1-2", "s1", EAN, MFN, "GPU"));
    }

    @Test
    void storeEntryResolvesForItsStoreOnly() {
        // given
        ProductsAddReview review = review(catalog());
        CategoryDefinition category = new CategoryDefinition().withName("GPU").withGeneratedId();

        // when
        ProductsAddReview.Prepared owner = review.prepare("s1", category, List.of(EAN), null);
        ProductsAddReview.Prepared other = review.prepare("s2", category, List.of(EAN), null);

        // then
        assertThat(owner.form().getProducts()).singleElement().extracting(ProductsBulkAddForm.Row::getName).isEqualTo("Store s1-2");
        assertThat(other.form().getProducts()).singleElement().extracting(ProductsBulkAddForm.Row::getName).isEqualTo("Taxonomy name");
    }

    @Test
    void pimEntryOfResolvesInTheStoresView() {
        // given
        ProductsAddReview review = review(catalog());
        Product product = new Product();
        product.setEan(EAN);
        product.setManufacturerCode(MFN);

        // when
        Optional<?> owner = review.pimEntryOf("s1", view, product);
        Optional<?> other = review.pimEntryOf("s2", view, product);

        // then
        assertThat(owner).isPresent();
        assertThat(other).isEmpty();
    }

    @Test
    void twoRowsLeadingToOneStoreEntryAddOneProduct() {
        // given: the store entry holds the EANs of both rows
        String secondEan = "5900000000998";
        ProductsAddReview review = review(ScopedPimCatalog.of(new PimEntry("s1-2",
                List.of(new PimIdentifier(EAN, PimIdentifierType.GTIN), new PimIdentifier(secondEan, PimIdentifierType.GTIN)),
                "Brand", "Store s1-2", "GPU", null, true, 2000, 2200, null, "s1")));
        CategoryDefinition category = new CategoryDefinition().withName("GPU").withGeneratedId();
        ProductsBulkAddForm form = ProductsBulkAddForm.of(List.of(
                new Product(null, null, EAN, "OTHER-1", "Brand", "L", "First", "Default"),
                new Product(null, null, secondEan, "OTHER-2", "Brand", "L", "Second", "Default")));

        // when
        int added = review.save("s1", category, form);

        // then
        assertThat(added).isEqualTo(1);
        org.mockito.Mockito.verify(productRepository, org.mockito.Mockito.times(1)).save(org.mockito.ArgumentMatchers.argThat(
                p -> "s1-2".equals(p.getPimId())));
    }

    private int addToCategoryHolding(String heldPimId, PimCatalog catalog) {
        ProductsAddReview review = review(catalog);
        when(productRepository.findAll(anyString())).thenReturn(List.of(new Product(null, heldPimId, null, null, "Brand", "L", "Held", "Default")));
        CategoryDefinition category = new CategoryDefinition().withName("GPU").withGeneratedId();
        ProductsBulkAddForm form = ProductsBulkAddForm.of(List.of(new Product(null, null, EAN, MFN, "Brand", "L", "New", "Default")));
        return review.save("s1", category, form);
    }

    @Test
    void addingTheCloneToACategoryHoldingTheSourceIsADuplicate() {
        // given: the category holds the global entry "g1", whose codes differ from the row's; the row's codes lead to the
        // store's clone "c1" of "g1"
        PimCatalog withClone = ScopedPimCatalog.of(
                ScopedPimCatalog.globalEntry("g1", ScopedPimCatalog.GLOBAL_GTIN, "M-590", "GPU"),
                ScopedPimCatalog.cloneEntry("c1", "s1", "g1", EAN, MFN, "GPU"));

        // when / then
        assertThat(addToCategoryHolding("g1", withClone)).isZero();
    }

    @Test
    void addingTheSourceToACategoryHoldingTheCloneIsADuplicate() {
        // given: the category holds the store's clone "c1", whose codes differ from the row's; the row's codes lead to its
        // source, the global entry "g1"
        PimCatalog withClone = ScopedPimCatalog.of(
                ScopedPimCatalog.globalEntry("g1", EAN, MFN, "GPU"),
                ScopedPimCatalog.cloneEntry("c1", "s1", "g1", EAN + "0", MFN + "0", "GPU"));

        // when / then
        assertThat(addToCategoryHolding("c1", withClone)).isZero();
    }

    @Test
    void otherStoresAreUnaffected() {
        // given: the category of s1 holds "c1", a clone of s2 that s1's view does not know, so it is no alias of "g2",
        // the entry the row's codes lead to
        PimCatalog catalog = ScopedPimCatalog.of(
                ScopedPimCatalog.globalEntry("g1", ScopedPimCatalog.GLOBAL_GTIN, "M-590", "GPU"),
                ScopedPimCatalog.cloneEntry("c1", "s2", "g1", EAN + "0", MFN + "0", "GPU"),
                ScopedPimCatalog.globalEntry("g2", EAN, MFN, "GPU"));

        // when / then
        assertThat(addToCategoryHolding("c1", catalog)).isEqualTo(1);
    }

    @Test
    void aliasesComeFromTheStoresViewOnly() {
        // given
        ProductsAddReview review = review(ScopedPimCatalog.of(
                ScopedPimCatalog.globalEntry("g1", ScopedPimCatalog.GLOBAL_GTIN, "M-590", "GPU"),
                ScopedPimCatalog.cloneEntry("c1", "s1", "g1", EAN, MFN, "GPU")));

        // when / then
        assertThat(review.aliasesOf("s1", "c1")).containsExactlyInAnyOrder("c1", "g1");
        assertThat(review.aliasesOf("s2", "c1")).containsExactly("c1");
    }

    @Test
    void reviewTakesNoEntryMatchingTheGroupByCodeOnly() {
        // given: the group's EAN is unknown to the PIM, its code is the global entry's
        ProductsAddReview review = review(ScopedPimCatalog.of(
                ScopedPimCatalog.globalEntry("g1", ScopedPimCatalog.GLOBAL_GTIN, MFN, "GPU")));
        CategoryDefinition category = new CategoryDefinition().withName("GPU").withGeneratedId();

        // when
        ProductsAddReview.Prepared prepared = review.prepare("s1", category, List.of(EAN), null);

        // then
        assertThat(prepared.form().getProducts()).singleElement().extracting(ProductsBulkAddForm.Row::getName).isEqualTo("Taxonomy name");
    }

    @Test
    void saveAttachesNoEntryMatchingTheGroupByCodeOnly() {
        // given: the group's EAN is unknown to the PIM, its code is the global entry's
        ProductsAddReview review = review(ScopedPimCatalog.of(
                ScopedPimCatalog.globalEntry("g1", ScopedPimCatalog.GLOBAL_GTIN, MFN, "GPU")));
        CategoryDefinition category = new CategoryDefinition().withName("GPU").withGeneratedId();
        ProductsBulkAddForm form = ProductsBulkAddForm.of(List.of(new Product(null, null, EAN, MFN, "Brand", "L", "New", "Default")));

        // when
        review.save("s1", category, form);

        // then
        ArgumentCaptor<Product> saved = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(saved.capture());
        assertThat(saved.getValue().getPimId()).isNull();
    }

    @Test
    void saveTakesNoGroupSharingOnlyTheProductsCode() {
        // given: the inventory does not know the product's EAN; its code is in the group of a PIM entry
        PimEntry g1 = ScopedPimCatalog.globalEntry("g1", ScopedPimCatalog.GLOBAL_GTIN, MFN, "GPU");
        ProductsAddReview review = review(ScopedPimCatalog.of(g1));
        MatchedInventory groupOfG1 = mock(MatchedInventory.class);
        when(groupOfG1.isEmpty()).thenReturn(false);
        when(groupOfG1.getInventoryKey()).thenReturn(InventoryKey.fromPimEntry(g1));
        when(view.findByInventoryKey(any())).thenReturn(groupOfG1);
        CategoryDefinition category = new CategoryDefinition().withName("GPU").withGeneratedId();
        ProductsBulkAddForm form = ProductsBulkAddForm.of(List.of(
                new Product(null, null, EAN_UNKNOWN_TO_PIM, MFN, "Brand", "L", "New", "Default")));

        // when
        review.save("s1", category, form);

        // then
        ArgumentCaptor<Product> saved = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(saved.capture());
        assertThat(saved.getValue().getPimId()).isNull();
    }

    @Test
    void productWithTheSameCodeButAnotherEanIsNotADuplicate() {
        // given
        ProductsAddReview review = review(ScopedPimCatalog.of());
        when(productRepository.findAll(anyString())).thenReturn(List.of(
                new Product(null, null, "5900000000111", MFN, "Brand", "L", "Held", "Default")));
        CategoryDefinition category = new CategoryDefinition().withName("GPU").withGeneratedId();
        ProductsBulkAddForm form = ProductsBulkAddForm.of(List.of(
                new Product(null, null, EAN_UNKNOWN_TO_PIM, MFN, "Brand", "L", "New", "Default")));

        // when
        int added = review.save("s1", category, form);

        // then
        assertThat(added).isEqualTo(1);
    }

    @Test
    void reviewKeepsAGroupSharingOnlyTheCode() {
        // given
        ProductsAddReview review = review(catalog());
        when(productRepository.findAll(anyString())).thenReturn(List.of(
                new Product(null, null, "5900000000111", MFN, "Brand", "L", "Held", "Default")));
        CategoryDefinition category = new CategoryDefinition().withName("GPU").withGeneratedId();

        // when
        ProductsAddReview.Prepared prepared = review.prepare("s2", category, List.of(EAN), null);

        // then
        assertThat(prepared.skippedExisting()).isEmpty();
        assertThat(prepared.form().getProducts()).hasSize(1);
    }

    @Test
    void productWithTheSameEanIsADuplicateDespiteAnotherPimId() {
        // given
        ProductsAddReview review = review(catalog());
        MatchedInventory groupOfG2 = mock(MatchedInventory.class);
        when(groupOfG2.isEmpty()).thenReturn(false);
        when(groupOfG2.getInventoryKey()).thenReturn(InventoryKey.fromPimIdAndEan("g2", EAN));
        when(view.findByEan(EAN)).thenReturn(groupOfG2);
        when(productRepository.findAll(anyString())).thenReturn(List.of(
                new Product(null, "g1", EAN, "OTHER-1", "Other", "L", "Held", "Default")));
        CategoryDefinition category = new CategoryDefinition().withName("GPU").withGeneratedId();

        // when
        ProductsAddReview.Prepared prepared = review.prepare("s2", category, List.of(EAN), null);

        // then
        assertThat(prepared.skippedExisting()).containsExactly(EAN);
    }

    @Test
    void pimEntryFoundOnlyByCodeIsNotAttached() {
        // given
        PimEntry kingston = globalEntryOfBrand("Kingston");

        // when
        Product saved = saveRow(kingston, EAN_UNKNOWN_TO_PIM, "Kingston");

        // then
        assertThat(saved.getPimId()).isNull();
    }

    @Test
    void pimEntryFoundByEanIsAttached() {
        // given
        PimEntry kingston = globalEntryOfBrand("Kingston");

        // when
        Product saved = saveRow(kingston, ScopedPimCatalog.GLOBAL_GTIN, "Trust");

        // then
        assertThat(saved.getPimId()).isEqualTo("g1");
        assertThat(saved.getBrand()).isEqualTo("Kingston");
    }

    private static PimEntry globalEntryOfBrand(String brand) {
        return new PimEntry("g1", ScopedPimCatalog.identifiers(ScopedPimCatalog.GLOBAL_GTIN, MFN), brand, "Global g1",
                "GPU", null, true, 1000, 1200);
    }

    private Product saveRow(PimEntry entry, String ean, String brand) {
        ProductsAddReview review = review(ScopedPimCatalog.of(entry));
        CategoryDefinition category = new CategoryDefinition().withName("GPU").withGeneratedId();
        ProductsBulkAddForm form = ProductsBulkAddForm.of(List.of(new Product(null, null, ean, MFN, brand, "L", "New", "Default")));
        review.save("s1", category, form);
        ArgumentCaptor<Product> saved = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(saved.capture());
        return saved.getValue();
    }
}
