package pl.commercelink.products;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.inventory.MatchedInventory;
import pl.commercelink.products.filters.InventoryFilterType;
import pl.commercelink.starter.dynamodb.Metadata;
import pl.commercelink.taxonomy.Taxonomy;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryDefinitionTest {

    @Mock
    private MatchedInventory matchedInventory;

    @Test
    void productLineFilterWithLowercaseBrandRejectsTheNamedLine() {
        // given
        InventoryDefinition definition = new InventoryDefinition(InventoryFilterType.PRODUCT_LINE_BY_BRAND_NOT_CONTAIN,
                List.of(new Metadata("gigabyte", "Windforce")));
        when(matchedInventory.getTaxonomy()).thenReturn(gpu("Gigabyte GeForce RTX 5070 Windforce OC"));

        // when
        boolean accepted = definition.test(matchedInventory);

        // then
        assertThat(accepted).isFalse();
    }

    @Test
    void productLineFilterWithLowercaseBrandKeepsOtherLinesOfTheBrand() {
        // given
        InventoryDefinition definition = new InventoryDefinition(InventoryFilterType.PRODUCT_LINE_BY_BRAND_NOT_CONTAIN,
                List.of(new Metadata("gigabyte", "Windforce")));
        when(matchedInventory.getTaxonomy()).thenReturn(gpu("Gigabyte GeForce RTX 5070 Gaming OC"));

        // when
        boolean accepted = definition.test(matchedInventory);

        // then
        assertThat(accepted).isTrue();
    }

    private static Taxonomy gpu(String name) {
        return new Taxonomy("5900000000001", "MFN-1", "Gigabyte", name, "GPU", 1, null, null);
    }
}
