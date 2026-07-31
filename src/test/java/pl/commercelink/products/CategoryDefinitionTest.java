package pl.commercelink.products;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CategoryDefinitionTest {

    @Test
    void definitionWithCategoryIsComplete() {
        // given
        CategoryDefinition definition = completeDefinitionWithoutCategory();
        definition.setCategory("Karty graficzne");

        // when / then
        assertThat(definition.isComplete()).isTrue();
    }

    @Test
    void definitionWithLegacyServicesCategoryStringIsComplete() {
        // given
        CategoryDefinition definition = completeDefinitionWithoutCategory();
        definition.setCategory("Services");

        // when / then
        assertThat(definition.isComplete()).isTrue();
    }

    @Test
    void definitionWithoutCategoryIsComplete() {
        // when / then
        assertThat(completeDefinitionWithoutCategory().isComplete()).isTrue();
    }

    @Test
    void categoriesDefaultToEmptyList() {
        // given
        CategoryDefinition definition = new CategoryDefinition();

        // when / then
        assertThat(definition.getCategories()).isEmpty();
    }

    @Test
    void withCategoriesSetsIds() {
        // given
        CategoryDefinition definition = new CategoryDefinition().withCategories("1234", "5678");

        // when / then
        assertThat(definition.getCategories()).containsExactly("1234", "5678");
    }

    @Test
    void completenessIgnoresCategories() {
        // given
        CategoryDefinition withoutCategories = completeDefinition();

        // when / then
        assertThat(withoutCategories.isComplete()).isTrue();
    }

    private CategoryDefinition completeDefinitionWithoutCategory() {
        StockDefinition stock = new StockDefinition();
        stock.setCriticalStockThreshold(1);
        stock.setLowStockThreshold(2);
        stock.setHighStockThreshold(3);

        AvailabilityDefinition availability = new AvailabilityDefinition();
        availability.setTotalMinQty(1);
        availability.setMinNumberOfProviders(1);

        PriceDefinition price = new PriceDefinition();
        price.setMultiplier(1.2);
        price.setPricingGroup("default");

        return new CategoryDefinition()
                .withName("Montaż")
                .withStockDefinition(stock)
                .withAvailabilityDefinition(availability)
                .withPriceDefinition(price);
    }

    private static CategoryDefinition completeDefinition() {
        return new CategoryDefinition()
                .withGeneratedId()
                .withName("Karty graficzne")
                .withStockDefinition(new StockDefinition(1, 10, 30))
                .withAvailabilityDefinition(new AvailabilityDefinition(3, 1))
                .withPriceDefinition(new PriceDefinition(1.00, 0, 0, 0, 0, PriceDefinition.DEFAULT_PRICING_GROUP));
    }
}
