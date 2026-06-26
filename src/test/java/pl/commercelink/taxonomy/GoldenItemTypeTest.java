package pl.commercelink.taxonomy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GOLDEN surface — {@link ItemType#of(String)} characterization.
 *
 * {@code ItemType.of} collapses the seven-valued {@link ProductGroup} (addressed by its string key)
 * into a binary product/service flag: the {@code Services} group maps to {@link ItemType#SERVICE},
 * every other group maps to {@link ItemType#PRODUCT}. This surface freezes that mapping
 * for every {@link ProductCategory} via its {@code getProductGroup()}.
 */
@ExtendWith(MockitoExtension.class)
class GoldenItemTypeTest {

    @Test
    void ofMapsEveryCategoryGroupToProductExceptServices() {
        // given / then
        for (ProductCategory category : ProductCategory.values()) {
            ItemType expected = category == ProductCategory.Services
                    ? ItemType.SERVICE
                    : ItemType.PRODUCT;
            assertThat(ItemType.of(category.getProductGroup().name())).isEqualTo(expected);
        }
    }

    @Test
    void ofMapsServicesGroupToServiceAndOthersToProduct() {
        // given / then
        for (ProductGroup group : ProductGroup.values()) {
            ItemType expected = group == ProductGroup.Services
                    ? ItemType.SERVICE
                    : ItemType.PRODUCT;
            assertThat(ItemType.of(group.name())).isEqualTo(expected);
        }
        // the single SERVICE-producing group, spelled out.
        assertThat(ItemType.of(ProductGroup.Services.name())).isEqualTo(ItemType.SERVICE);
        assertThat(ItemType.of(ProductGroup.PcComponents.name())).isEqualTo(ItemType.PRODUCT);
    }
}
