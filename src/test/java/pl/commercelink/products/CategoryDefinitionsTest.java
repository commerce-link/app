package pl.commercelink.products;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.products.filters.InventoryFilterType;
import pl.commercelink.starter.dynamodb.Metadata;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryDefinitionsTest {

    @Mock
    private ProductCatalogRepository catalogs;
    @Mock
    private ProductRepository products;
    @InjectMocks
    private CategoryDefinitions definitions;

    private ProductCatalog catalog;
    private CategoryDefinition gpu;

    @BeforeEach
    void setUp() {
        catalog = new ProductCatalog("store", "Parts");
        gpu = new CategoryDefinition().withName("GPU").withGeneratedId().withSequenceNumber(1)
                .withStockDefinition(new StockDefinition(2, 4, 10))
                .withAvailabilityDefinition(new AvailabilityDefinition(3, 1))
                .withPriceDefinition(new PriceDefinition(1.05, 49, 0, 0, 0, "Default"))
                .withMarketplaceDefinition(new MarketplaceDefinition("allegro", 1.1, 0, 1, 2, 0, 3));
        gpu.getInventoryDefinitions().add(new InventoryDefinition(InventoryFilterType.BRAND_NAME, List.of(new Metadata("Brands", "MSI"))));
        catalog.getCategories().add(gpu);
    }

    @Test
    void createUsesTheDefaultPricingAndTheNextSequenceNumber() {
        // when
        CategoryDefinition created = definitions.create(catalog, new CategoryDefinitions.Basics(
                "CPU", 0, List.of("pim-cpu"), CategoryDefinitionType.Managed, 1, false, false, true, List.of()));

        // then
        assertThat(created.getCategoryId()).isNotBlank();
        assertThat(created.getSequenceNumber()).isEqualTo(2);
        assertThat(created.getStockDefinition().getCriticalStockThreshold()).isEqualTo(1);
        assertThat(created.getStockDefinition().getHighStockThreshold()).isEqualTo(30);
        assertThat(created.getAvailabilityDefinition().getTotalMinQty()).isEqualTo(3);
        assertThat(created.getPriceDefinitions()).extracting(PriceDefinition::getPricingGroup).containsExactly("Default");
        assertThat(created.isComplete()).isTrue();
        assertThat(catalog.getCategories()).contains(created);
        verify(catalogs).save(catalog);
    }

    @Test
    void saveBasicsLeavesPricingMarketplacesAndFiltersUntouched() {
        // when
        definitions.saveBasics(catalog, gpu, new CategoryDefinitions.Basics(
                "Karty graficzne", 5, List.of("pim-gpu"), CategoryDefinitionType.Managed, 3, true, false, false, List.of("RTX 5060", "", "RTX 5070", "RTX 5060")));

        // then
        assertThat(gpu.getName()).isEqualTo("Karty graficzne");
        assertThat(gpu.getSequenceNumber()).isEqualTo(5);
        assertThat(gpu.getMaxQty()).isEqualTo(3);
        assertThat(gpu.isRequiredDuringOrder()).isTrue();
        assertThat(gpu.isDeletionProtection()).isFalse();
        assertThat(gpu.getGroupingOrder()).containsExactly("RTX 5060", "RTX 5070");
        assertThat(gpu.getPimCategoryIds()).containsExactly("pim-gpu");
        assertThat(gpu.getTypeChangedAt()).isNull();
        assertThat(gpu.getPriceDefinitions()).hasSize(1);
        assertThat(gpu.getMarketplaceDefinitions()).hasSize(1);
        assertThat(gpu.getInventoryDefinitions()).hasSize(1);
        verify(catalogs).save(catalog);
    }

    @Test
    void changingTheTypeStampsTypeChangedAt() {
        // when
        definitions.saveBasics(catalog, gpu, new CategoryDefinitions.Basics(
                "GPU", 1, List.of(), CategoryDefinitionType.Dynamic, 1, false, false, true, List.of()));

        // then
        assertThat(gpu.getType()).isEqualTo(CategoryDefinitionType.Dynamic);
        assertThat(gpu.getTypeChangedAt()).isNotNull();
    }

    @Test
    void savePricingReplacesOnlyThePricingSection() {
        // when
        definitions.savePricing(catalog, gpu, new StockDefinition(1, 10, 30), new AvailabilityDefinition(1, 1),
                List.of(new PriceDefinition(1.0, 0, 0, 0, 0, "Default"), new PriceDefinition(1.1, 99, 30, 20, 10, "Premium")));

        // then
        assertThat(gpu.getPriceDefinitions()).extracting(PriceDefinition::getPricingGroup).containsExactlyInAnyOrder("Default", "Premium");
        assertThat(gpu.getStockDefinition().getHighStockThreshold()).isEqualTo(30);
        assertThat(gpu.getName()).isEqualTo("GPU");
        assertThat(gpu.getMarketplaceDefinitions()).hasSize(1);
    }

    @Test
    void saveMarketplaceReplacesTheDefinitionWithTheSameName() {
        // when
        definitions.saveMarketplace(catalog, gpu, new MarketplaceDefinition("allegro", 1.2, 0, 1, 3, 1, 0));
        definitions.saveMarketplace(catalog, gpu, new MarketplaceDefinition("empik", 1.1, 0, 1, 2, 0, 0));

        // then
        assertThat(gpu.getMarketplaceDefinitions()).hasSize(2);
        assertThat(gpu.getCategoryDefinition("allegro")).get().extracting(MarketplaceDefinition::getMarkup).isEqualTo(1.2);
    }

    @Test
    void removeMarketplaceDropsUnnamedDefinitionsWhenAskedForNull() {
        // given
        gpu.getMarketplaceDefinitions().add(new MarketplaceDefinition(null, 1.0, 0, 5, 3, 0, 0));

        // when
        definitions.removeMarketplace(catalog, gpu, null);

        // then
        assertThat(gpu.getMarketplaceDefinitions()).extracting(MarketplaceDefinition::getName).containsExactly("allegro");
    }

    @Test
    void removeDeletesProductsUnlessAnotherCategorySharesAPimCategory() {
        // given
        gpu.setDeletionProtection(false);
        gpu.setPimCategoryIds(List.of("pim-gpu"));
        CategoryDefinition twin = new CategoryDefinition().withName("GPU 2").withGeneratedId();
        twin.setPimCategoryIds(List.of("pim-gpu"));
        catalog.getCategories().add(twin);

        // when
        CategoryDefinitions.RemoveResult result = definitions.remove(catalog, gpu);

        // then
        assertThat(result.productsKept()).isTrue();
        assertThat(catalog.getCategories()).doesNotContain(gpu);
        verify(products, never()).delete(any(List.class));
    }

    @Test
    void protectedCategoryCannotBeRemoved() {
        // when / then
        assertThatThrownBy(() -> definitions.remove(catalog, gpu)).isInstanceOf(IllegalStateException.class);
        verify(catalogs, never()).save(any());
    }

    @Test
    void countsProductsUsingAPriceGroup() {
        // given
        when(products.findAll(gpu.getCategoryId())).thenReturn(List.of(
                new Product(gpu.getCategoryId(), "p1", "1", "m", "b", "l", "n", "Premium"),
                new Product(gpu.getCategoryId(), "p2", "2", "m", "b", "l", "n", "Default")));

        // when / then
        assertThat(definitions.productsInPriceGroup(gpu, "Premium")).isEqualTo(1);
        assertThat(definitions.productsInPriceGroup(gpu, "Ultra")).isZero();
    }
}
