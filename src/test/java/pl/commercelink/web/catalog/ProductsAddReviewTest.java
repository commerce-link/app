package pl.commercelink.web.catalog;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
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
import pl.commercelink.pim.api.PimCatalog;
import pl.commercelink.products.CatalogPlacement;
import pl.commercelink.products.CategoryDefinition;
import pl.commercelink.products.PriceDefinition;
import pl.commercelink.products.Product;
import pl.commercelink.products.ProductRepository;
import pl.commercelink.products.brand.BrandMapper;
import pl.commercelink.taxonomy.Taxonomy;
import pl.commercelink.web.dtos.ProductsBulkAddForm;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProductsAddReviewTest {

    private static final String STORE_ID = "store-1";

    @Mock private ProductRepository productRepository;
    @Mock private Inventory inventory;
    @Mock private InventoryView view;
    @Mock private PimCatalog pimCatalog;
    @Mock private BrandMapper brandMapper;
    @Mock private MessageSource messageSource;
    @Mock private CatalogPlacement catalogPlacement;
    @InjectMocks private ProductsAddReview review;

    private CategoryDefinition gpu;

    @BeforeEach
    void setUp() {
        gpu = new CategoryDefinition().withName("GPU").withGeneratedId();
        gpu.setPriceDefinitions(new ArrayList<>(List.of(new PriceDefinition(1.0, 0, 0, 0, 0, "Default"),
                new PriceDefinition(1.0, 0, 0, 0, 0, "Premium"))));
        when(inventory.withEnabledSuppliersOnly(STORE_ID)).thenReturn(view);
        MatchedInventory gone = mock(MatchedInventory.class);
        when(gone.isEmpty()).thenReturn(true);
        when(view.findByEan(anyString())).thenReturn(gone);
        when(pimCatalog.findByPimIdOrGtinsOrMpns(any(), any(), any())).thenReturn(Optional.empty());
        when(productRepository.findAll(anyString())).thenReturn(List.of());
        product("1", "MSI RTX 5070");
        product("2", "ASUS RTX 5060");
    }

    @Test
    void rowsKeepTheirInventoryEanAndSkipWhatTheCategoryHasAndWhatTheInventoryLost() {
        // given
        when(productRepository.findAll(gpu.getCategoryId())).thenReturn(List.of(
                new Product(gpu.getCategoryId(), "pim", "2", "M-2", "ASUS", "l", "ASUS RTX 5060", "Default")));

        // when
        ProductsAddReview.Prepared prepared = review.prepare(STORE_ID, gpu, List.of("1", "2", "3"), null);

        // then
        assertThat(prepared.form().getProducts()).extracting(ProductsBulkAddForm.Row::getSourceEan).containsExactly("1");
        assertThat(prepared.skippedExisting()).containsExactly("2");
        assertThat(prepared.skipped()).containsExactly("3");
        assertThat(prepared.resetRows()).isZero();
    }

    /** The new category may skip other rows, so what was typed follows the product it was typed for, not its index. */
    @Test
    void anotherCategoryKeepsTheTypedNameAndIdentifiersOfEachProductAndTheReviewId() {
        // given
        ProductsBulkAddForm edited = edited(row("2", "Typed two", "2", "M-2", "ASUS RTX 5060", "Default"),
                row("1", "Typed one", "5901234567890", "X-1", "MSI RTX 5070", "Default"));

        // when
        ProductsAddReview.Prepared prepared = review.prepare(STORE_ID, gpu, List.of("1", "2"), edited);

        // then
        assertThat(prepared.form().getProducts()).extracting(ProductsBulkAddForm.Row::getSourceEan).containsExactly("1", "2");
        assertThat(prepared.form().getProducts()).extracting(ProductsBulkAddForm.Row::getName).containsExactly("Typed one", "Typed two");
        assertThat(prepared.form().getProducts().get(0).getEan()).isEqualTo("5901234567890");
        assertThat(prepared.form().getProducts().get(0).getManufacturerCode()).isEqualTo("X-1");
        assertThat(prepared.form().getReviewId()).isEqualTo(edited.getReviewId());
        assertThat(prepared.resetRows()).isZero();
    }

    @Test
    void labelAndPricingGroupStayWhenTheNewCategoryHasThemAndGoBackToTheProposalOtherwise() {
        // given
        gpu.setGroupingOrder(new ArrayList<>(List.of("RTX 5070", "MSI RTX 5070")));
        ProductsBulkAddForm edited = edited(row("1", "n", "1", "M-1", "RTX 5070", "premium"),
                row("2", "n", "2", "M-2", "Old subcategory", "Gold"));

        // when
        ProductsAddReview.Prepared prepared = review.prepare(STORE_ID, gpu, List.of("1", "2"), edited);

        // then
        ProductsBulkAddForm.Row kept = prepared.form().getProducts().get(0);
        ProductsBulkAddForm.Row reset = prepared.form().getProducts().get(1);
        assertThat(kept.getLabel()).isEqualTo("RTX 5070");
        assertThat(kept.getPricingGroup()).isEqualTo("Premium");
        assertThat(reset.getLabel()).isEqualTo("ASUS RTX 5060");
        assertThat(reset.getPricingGroup()).isEqualTo("Default");
        assertThat(prepared.resetRows()).isEqualTo(1);
    }

    /** A category that groups by nothing shows the label as a text field: whatever was typed there passes. */
    @Test
    void aCategoryWithoutSubcategoriesKeepsAnyTypedLabel() {
        // given
        ProductsBulkAddForm edited = edited(row("1", "n", "1", "M-1", "Anything", "Default"));

        // when
        ProductsAddReview.Prepared prepared = review.prepare(STORE_ID, gpu, List.of("1"), edited);

        // then
        assertThat(prepared.form().getProducts().get(0).getLabel()).isEqualTo("Anything");
        assertThat(prepared.resetRows()).isZero();
    }

    private void product(String ean, String name) {
        MatchedInventory matched = mock(MatchedInventory.class);
        when(matched.isEmpty()).thenReturn(false);
        when(matched.getInventoryKey()).thenReturn(new InventoryKey(ean, "M-" + ean));
        when(matched.getTaxonomy()).thenReturn(new Taxonomy(ean, "M-" + ean, "Brand", name, "GPU", 1, null, null));
        when(matched.getLowestPrice()).thenReturn(Price.fromGross(1000));
        when(view.findByEan(ean)).thenReturn(matched);
    }

    private static ProductsBulkAddForm edited(ProductsBulkAddForm.Row... rows) {
        ProductsBulkAddForm form = ProductsBulkAddForm.of(List.of());
        form.setProducts(new ArrayList<>(List.of(rows)));
        return form;
    }

    private static ProductsBulkAddForm.Row row(String sourceEan, String name, String ean, String mfn, String label, String group) {
        ProductsBulkAddForm.Row row = new ProductsBulkAddForm.Row();
        row.setSourceEan(sourceEan);
        row.setName(name);
        row.setEan(ean);
        row.setManufacturerCode(mfn);
        row.setLabel(label);
        row.setPricingGroup(group);
        return row;
    }
}
