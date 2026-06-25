package pl.commercelink.taxonomy;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.inventory.supplier.api.Taxonomy;

import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaxonomyCacheCategoryKeyMergeTest {

    @Mock
    private TaxonomyRepository taxonomyRepository;

    @Test
    void categoryKeySurvivesAWeightMergeRebuild() {
        // given - two same-MFN taxonomies with differing weights force withWeights() to rebuild the winner
        when(taxonomyRepository.loadNewest()).thenReturn(Pair.of("N/A", new ArrayList<>()));
        TaxonomyCache cache = new TaxonomyCache(taxonomyRepository);
        cache.onStartUp();
        cache.add(new Taxonomy("E1", "MFN1", "B", "N", ProductCategory.Other, 5, 100, null, "Cables356k"));
        cache.add(new Taxonomy("E2", "MFN1", "B", "N", ProductCategory.Other, 9, null, 200, "Cables356k"));

        // when
        Taxonomy merged = cache.findByMfn("MFN1");

        // then
        assertThat(merged.categoryKey()).isEqualTo("Cables356k");
        assertThat(merged.netWeightInGrams()).isEqualTo(100);
        assertThat(merged.grossWeightInGrams()).isEqualTo(200);
    }
}
