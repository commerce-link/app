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
}
