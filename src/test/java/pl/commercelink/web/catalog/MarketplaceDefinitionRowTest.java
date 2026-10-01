package pl.commercelink.web.catalog;

import org.junit.jupiter.api.Test;
import pl.commercelink.products.MarketplaceDefinition;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class MarketplaceDefinitionRowTest {

    @Test
    void statesFollowTheDefinition() {
        // given
        MarketplaceDefinition complete = new MarketplaceDefinition("Allegro", 1.1, 5, 1, 2, 1, 3);
        MarketplaceDefinition disabled = new MarketplaceDefinition("Empik", 1.12, 3, 1, 2, 0, 0);
        disabled.setEnabled(false);
        MarketplaceDefinition incomplete = new MarketplaceDefinition("Morele", 1.05, 0, 0, 0, 0, 0);

        // when / then
        assertThat(MarketplaceDefinitionRow.of("c", "k", "Allegro", "Allegro", Optional.of(complete), 14, true).state()).isEqualTo(MarketplaceDefinitionRow.State.EXPORTING);
        assertThat(MarketplaceDefinitionRow.of("c", "k", "Empik", "Empik", Optional.of(disabled), 0, true).state()).isEqualTo(MarketplaceDefinitionRow.State.DISABLED);
        assertThat(MarketplaceDefinitionRow.of("c", "k", "Morele", "Morele", Optional.of(incomplete), 0, true).state()).isEqualTo(MarketplaceDefinitionRow.State.INCOMPLETE);
        assertThat(MarketplaceDefinitionRow.of("c", "k", "CS-Cart", "Sklep partnera", Optional.empty(), 0, true).state()).isEqualTo(MarketplaceDefinitionRow.State.NOT_CONFIGURED);
        assertThat(MarketplaceDefinitionRow.of("c", "k", null, null, Optional.of(incomplete), 0, false).state()).isEqualTo(MarketplaceDefinitionRow.State.ORPHANED);
    }

    @Test
    void hrefsEncodeTheMarketplaceNameAndUseAPlaceholderForUnnamed() {
        // when / then
        assertThat(MarketplaceDefinitionRow.of("c", "k", "Sklep partnera", "x", Optional.empty(), 0, true).editHref())
                .isEqualTo("/dashboard/catalogs/c/category/k/settings/marketplaces/Sklep%20partnera");
        assertThat(MarketplaceDefinitionRow.of("c", "k", null, null, Optional.of(new MarketplaceDefinition()), 0, false).deleteHref())
                .isEqualTo("/dashboard/catalogs/c/category/k/settings/marketplaces/_unnamed_/delete");
    }
}
