package pl.commercelink.products;

import org.junit.jupiter.api.Test;

import java.util.List;

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

    /**
     * RF-17: the groups are kept in the order they are matched in, the higher price threshold first. Groups with the
     * same threshold keep the order they were given in (the order of the form), not the alphabet of their names.
     */
    @Test
    void groupsWithTheSamePriceThresholdKeepTheOrderTheyWereGivenIn() {
        // given
        CategoryDefinition category = new CategoryDefinition();

        // when
        category.setPriceDefinitions(List.of(group("Zeta", 0), group("Default", 0), group("Ultra", 2000),
                group("Beta", 2000), group("Alpha", 0)));

        // then
        assertThat(category.getPriceDefinitions()).extracting(PriceDefinition::getPricingGroup)
                .containsExactly("Ultra", "Beta", "Zeta", "Default", "Alpha");
    }

    /** A group read back without a name (old data) does not break the ordering of the others. */
    @Test
    void aGroupWithoutANameIsOrderedLikeTheOthers() {
        // given
        CategoryDefinition category = new CategoryDefinition();

        // when
        category.setPriceDefinitions(List.of(group(null, 0), group("Ultra", 2000)));

        // then
        assertThat(category.getPriceDefinitions()).extracting(PriceDefinition::getPricingGroup).containsExactly("Ultra", null);
    }

    private static PriceDefinition group(String name, double priceMatch) {
        PriceDefinition definition = new PriceDefinition(1.1, 0, 0, 0, 0, name);
        definition.setPriceMatch(priceMatch);
        return definition;
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
