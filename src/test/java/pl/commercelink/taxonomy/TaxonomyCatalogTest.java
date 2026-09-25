package pl.commercelink.taxonomy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaxonomyCatalogTest {

    @Mock
    private TaxonomyCatalogRepository repository;

    @InjectMocks
    private TaxonomyCatalog catalog;

    @Test
    void findByMfnSkipsTheTableForBlankCodes() {
        // when / then
        assertThat(catalog.findByMfn(null)).isNull();
        assertThat(catalog.findByMfn("  ")).isNull();
        verifyNoInteractions(repository);
    }

    @Test
    void findByMfnsDropsBlanksAndDuplicatesBeforeReading() {
        // given
        when(repository.findAll(List.of("MFN-1", "MFN-2"))).thenReturn(Map.of());

        // when
        catalog.findByMfns(Arrays.asList("MFN-1", "MFN-1", "", "MFN-2", null));

        // then
        verify(repository).findAll(List.of("MFN-1", "MFN-2"));
    }

    @Test
    void findByMfnsSkipsTheTableWhenNothingIsWorthReading() {
        // when
        Map<String, Taxonomy> found = catalog.findByMfns(List.of("", "   "));

        // then
        assertThat(found).isEmpty();
        verify(repository, never()).findAll(any());
    }

    @Test
    void findBestPrefersACategorizedRecordOverABetterScoredPendingOne() {
        // given
        Taxonomy pending = new Taxonomy("1234567890123", "MFN-PENDING", "Brand", "Name", null, 1, null, null);
        Taxonomy categorized = new Taxonomy("1234567890123", "MFN-CAT", "Brand", "Name", "CPU", 10, null, null);
        when(repository.findAll(any())).thenReturn(Map.of("MFN-PENDING", pending, "MFN-CAT", categorized));

        // when
        Taxonomy best = catalog.findBest(Set.of("MFN-PENDING", "MFN-CAT"));

        // then
        assertThat(best.category()).isEqualTo("CPU");
    }

    @Test
    void findBestFallsBackToEmptyWhenTheTableKnowsNothing() {
        // given
        when(repository.findAll(any())).thenReturn(Map.of());

        // when
        Taxonomy best = catalog.findBest(Set.of("MFN-GONE"));

        // then
        assertThat(best).isSameAs(Taxonomy.EMPTY);
    }

    @Test
    void commitBatchesOnlyTheCategorizedRecordsTheMergeChanged() {
        // given
        Taxonomy untouched = new Taxonomy("1234567890123", "MFN-1", "Brand", "Name", "CPU", 10, null, null);
        Taxonomy improved = new Taxonomy("1234567890123", "MFN-2", "Brand", "Name2", "GPU", 10, null, null);
        when(repository.findAll(List.of("MFN-1", "MFN-2"))).thenReturn(Map.of("MFN-1", untouched, "MFN-2", improved));
        TaxonomyMerge merge = catalog.openMerge(List.of("MFN-1", "MFN-2"));
        merge.apply(untouched);
        merge.apply(new Taxonomy("1234567890123", "MFN-2", "Brand", "Better", "GPU", 1, null, null));

        // when
        catalog.commit(merge);

        // then
        ArgumentCaptor<List<Taxonomy>> written = ArgumentCaptor.captor();
        verify(repository).saveAll(written.capture());
        assertThat(written.getValue()).extracting(Taxonomy::mfn).containsExactly("MFN-2");
        verify(repository, never()).saveIfCategoryUnchanged(any(), any());
    }

    @Test
    void commitGuardsRecordsThatHadNoCategoryWhenTheChunkReadThem() {
        // given
        Taxonomy pending = new Taxonomy("1234567890123", "MFN-1", "Brand", "Name", null, 10, null, null);
        when(repository.findAll(List.of("MFN-1"))).thenReturn(Map.of("MFN-1", pending));
        when(repository.saveIfCategoryUnchanged(any(), any())).thenReturn(true);
        TaxonomyMerge merge = catalog.openMerge(List.of("MFN-1"));
        Taxonomy improved = new Taxonomy("1234567890123", "MFN-1", "Brand", "Better", null, 1, null, null);
        merge.apply(improved);

        // when
        catalog.commit(merge);

        // then
        verify(repository).saveIfCategoryUnchanged(improved, pending);
        verify(repository).saveAll(List.of());
    }

    @Test
    void commitRetriesAgainstTheFreshRecordWhenACategoryLandedMidChunk() {
        // given
        Taxonomy pending = new Taxonomy("1234567890123", "MFN-1", "Brand", "Name", null, 10, null, null);
        Taxonomy categorizedMeanwhile =
                new Taxonomy("1234567890123", "MFN-1", "Brand", "Name", "CPU", 10, null, null, null, "301");
        when(repository.findAll(List.of("MFN-1"))).thenReturn(Map.of("MFN-1", pending));
        when(repository.saveIfCategoryUnchanged(any(), any())).thenReturn(false, true);
        when(repository.find("MFN-1")).thenReturn(categorizedMeanwhile);
        TaxonomyMerge merge = catalog.openMerge(List.of("MFN-1"));
        merge.apply(new Taxonomy("1234567890123", "MFN-1", "Brand", "Better", null, 1, null, null));

        // when
        catalog.commit(merge);

        // then
        ArgumentCaptor<Taxonomy> retried = ArgumentCaptor.captor();
        verify(repository, times(2)).saveIfCategoryUnchanged(retried.capture(), any());
        assertThat(retried.getValue().category()).isEqualTo("CPU");
        assertThat(retried.getValue().categoryId()).isEqualTo("301");
    }

    @Test
    void updateCategoryRefusesIncompleteResolutions() {
        // when / then
        assertFalse(catalog.updateCategory("", "CPU", "301"));
        assertFalse(catalog.updateCategory("   ", "CPU", "301"));
        assertFalse(catalog.updateCategory("MFN-1", null, "301"));
        assertFalse(catalog.updateCategory("MFN-1", "CPU", null));
        assertFalse(catalog.updateCategory("MFN-1", "CPU", " "));
        verify(repository, never()).updateCategoryIfAbsent(anyString(), anyString(), anyString());
    }

    @Test
    void updateCategoryDelegatesTheConditionalWriteToTheTable() {
        // given
        when(repository.updateCategoryIfAbsent("MFN-1", "Cokolwiek", "999")).thenReturn(true);

        // when / then
        assertTrue(catalog.updateCategory("MFN-1", "Cokolwiek", "999"));
    }

    @Test
    void updateCategoryReportsARefusedConditionalWrite() {
        // given
        when(repository.updateCategoryIfAbsent("MFN-1", "CPU", "301")).thenReturn(false);

        // when / then
        assertFalse(catalog.updateCategory("MFN-1", "CPU", "301"));
    }
}
