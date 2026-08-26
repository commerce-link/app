package pl.commercelink.products;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MarketplaceDefinitionTest {

    @Test
    void isCompleteWhenOnlyDistributorsCriteriaAreSet() {
        // given
        MarketplaceDefinition definition = new MarketplaceDefinition("Morele", 1.1, 0, 5, 2, 0);

        // when / then
        assertThat(definition.isComplete()).isTrue();
        assertThat(definition.hasDistributorsCriteria()).isTrue();
        assertThat(definition.hasWarehouseCriteria()).isFalse();
    }

    @Test
    void isCompleteWhenOnlyWarehouseCriteriaAreSet() {
        // given
        MarketplaceDefinition definition = new MarketplaceDefinition("Morele", 1.1, 0, 0, 0, 3);

        // when / then
        assertThat(definition.isComplete()).isTrue();
        assertThat(definition.hasDistributorsCriteria()).isFalse();
        assertThat(definition.hasWarehouseCriteria()).isTrue();
    }

    @Test
    void isNotCompleteWhenDistributorsCriteriaArePartiallySet() {
        // given
        MarketplaceDefinition definition = new MarketplaceDefinition("Morele", 1.1, 0, 5, 0, 0);

        // when / then
        assertThat(definition.isComplete()).isFalse();
        assertThat(definition.hasDistributorsCriteria()).isFalse();
    }

    @Test
    void isNotCompleteWithoutNameOrMarkup() {
        // when / then
        assertThat(new MarketplaceDefinition(null, 1.1, 0, 5, 2, 3).isComplete()).isFalse();
        assertThat(new MarketplaceDefinition("Morele", 0, 0, 5, 2, 3).isComplete()).isFalse();
    }
}
