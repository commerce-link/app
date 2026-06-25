package pl.commercelink.taxonomy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GOLDEN characterization test for surface P2-CategoryTreeCache (SCAFFOLD, additive).
 * Freezes the CURRENT behaviour of {@link CategoryTreeCache}: ordinal lookup and group
 * resolution seeded from {@link ProductCategory}, with MAX-style tolerance for null /
 * unknown keys (no exceptions escape).
 *
 * TEST-ONLY: assertions are aligned to real behaviour, no production code is changed.
 */
@ExtendWith(MockitoExtension.class)
class GoldenCategoryTreeCacheTest {

    private final CategoryTreeCache cache = new CategoryTreeCache();

    @Test
    void sequenceNumberAndGroupMatchEnumForEveryCategory() {
        // given / when / then
        for (ProductCategory category : ProductCategory.values()) {
            assertThat(cache.sequenceNumberOf(category.name())).isEqualTo(category.ordinal());
            assertThat(cache.groupForCategoryKey(category.name()))
                    .isEqualTo(category.getProductGroup().name());
        }
    }

    @Test
    void sequenceNumberOfOtherEqualsItsOrdinal() {
        // when / then
        assertThat(cache.sequenceNumberOf("Other")).isEqualTo(10);
    }

    @Test
    void sequenceNumberOfNullIsMaxValue() {
        // when / then
        assertThat(cache.sequenceNumberOf(null)).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    void sequenceNumberOfUnknownKeyIsMaxValueWithoutThrowing() {
        // when / then
        assertThat(cache.sequenceNumberOf("NieznanyKlucz")).isEqualTo(Integer.MAX_VALUE);
    }
}
