package pl.commercelink.taxonomy;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.inventory.InventoryKey;
import pl.commercelink.inventory.supplier.api.Taxonomy;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class GoldenTaxonomyTieBreakTest {

    @Mock
    private TaxonomyRepository taxonomyRepository;

    @Test
    void bestByScoreIncomingWinsTieWhenScoresEqual() {
        // given
        lenient().when(taxonomyRepository.loadNewest()).thenReturn(Pair.of("N/A", new ArrayList<>()));
        TaxonomyCache cache = new TaxonomyCache(taxonomyRepository);
        cache.onStartUp();

        Taxonomy first = new Taxonomy("E1", "M1", "B", "First", ProductCategory.Other, 5);
        Taxonomy second = new Taxonomy("E2", "M1", "B", "Second", ProductCategory.Other, 5);

        // when
        cache.add(first);
        cache.add(second);

        // then
        // bestByScore: `b.dataAccuracyScore() <= a.dataAccuracyScore() ? b : a` with a=current, b=incoming.
        // On a tie the incoming (second) wins because of the inclusive <=.
        // Mutating <= to < would flip the winner to "First".
        Taxonomy result = cache.findByMfn("M1");
        assertThat(result.name()).isEqualTo("Second");
        assertThat(result.dataAccuracyScore()).isEqualTo(5);
    }

    @Test
    void findFirstSeenWinsTieWhenScoresEqual() {
        // given
        lenient().when(taxonomyRepository.loadNewest()).thenReturn(Pair.of("N/A", new ArrayList<>()));
        TaxonomyCache cache = new TaxonomyCache(taxonomyRepository);
        cache.onStartUp();

        // Two distinct mfns mapped to taxonomies with an EQUAL score.
        Taxonomy alpha = new Taxonomy("EA", "ALPHA", "B", "Alpha", ProductCategory.Other, 7);
        Taxonomy beta = new Taxonomy("EB", "BETA", "B", "Beta", ProductCategory.Other, 7);
        cache.add(alpha);
        cache.add(beta);

        InventoryKey key = new InventoryKey(Set.of(), Set.of("ALPHA", "BETA"));

        // find iterates key.getProductCodes() (a Set, hash-order) and replaces only on a STRICT `<`.
        // With equal scores the first code encountered in iteration order wins.
        // We resolve the actual hash iteration order empirically and freeze the real winner.
        Iterator<String> iterator = key.getProductCodes().iterator();
        String firstSeenCode = iterator.next();
        String expectedName = firstSeenCode.equals("ALPHA") ? "Alpha" : "Beta";

        // when
        Taxonomy result = cache.find(key);

        // then
        // first-seen wins the tie (strict <); flipping < to <= would yield the LAST-seen taxonomy instead.
        assertThat(result.name()).isEqualTo(expectedName);
        assertThat(result.dataAccuracyScore()).isEqualTo(7);
    }
}
