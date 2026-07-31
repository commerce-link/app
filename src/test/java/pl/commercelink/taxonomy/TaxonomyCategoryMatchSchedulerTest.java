package pl.commercelink.taxonomy;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.pim.api.CategoryMatchRequest;
import pl.commercelink.pim.api.PimCatalog;
import pl.commercelink.taxonomy.mapping.CategoryMappingCache;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaxonomyCategoryMatchSchedulerTest {

    private TaxonomyCache cache;

    @Mock
    private TaxonomyRepository taxonomyRepository;

    @Mock
    private PimCatalog pimCatalog;

    @Mock
    private CategoryMappingCache mappingCache;

    @BeforeEach
    void setUp() {
        Mockito.when(taxonomyRepository.loadNewest()).thenReturn(Pair.of("N/A", new ArrayList<>()));
        cache = new TaxonomyCache(taxonomyRepository);
        cache.onStartUp();
    }

    private TaxonomyCategoryMatchScheduler scheduler(TaxonomyCategoryMatchProperties properties) {
        return new TaxonomyCategoryMatchScheduler(cache, pimCatalog, properties,
                new TaxonomyCategoryEnrichment(cache, properties, mappingCache), mappingCache);
    }

    private TaxonomyCategoryEnrichment enrichmentFor(TaxonomyCategoryMatchProperties properties) {
        return new TaxonomyCategoryEnrichment(cache, properties, mappingCache);
    }

    private static String mfnWhere(java.util.function.IntPredicate trickleResidue) {
        return IntStream.range(0, 1000).mapToObj(i -> "MFN-" + i)
                .filter(mfn -> trickleResidue.test(Math.floorMod(mfn.hashCode(), 20)))
                .findFirst().orElseThrow();
    }

    @Test
    void everyPendingRowIsSubmittedExactlyOnceAcrossFullBucketCycle() {
        // given
        List<String> pendingMfns = IntStream.range(0, 20).mapToObj(i -> "MFN-P-" + i).toList();
        pendingMfns.forEach(mfn -> cache.add(pending(mfn)));
        cache.add(new Taxonomy("1234567890123", "MFN-CAT", "Brand", "Name", "CPU", 5, null, null));
        TaxonomyCategoryMatchScheduler scheduler = scheduler(new TaxonomyCategoryMatchProperties(4, 300000));

        // when
        for (int i = 0; i < 4; i++) {
            scheduler.sweep();
        }

        // then
        ArgumentCaptor<CategoryMatchRequest> captor = ArgumentCaptor.forClass(CategoryMatchRequest.class);
        verify(pimCatalog, atLeastOnce()).submitCategoryMatch(captor.capture());
        List<String> submittedMfns = captor.getAllValues().stream().map(CategoryMatchRequest::mfn).toList();
        assertThat(submittedMfns).hasSize(20);
        assertThat(Set.copyOf(submittedMfns)).isEqualTo(pendingMfns.stream().collect(Collectors.toSet()));
        assertThat(submittedMfns).doesNotContain("MFN-CAT");
    }

    @Test
    void requestCarriesIdentifiersFromCacheRow() {
        // given
        cache.add(new Taxonomy("1234567890123", "MFN-1", "Brand", "Name", null, 5, null, null));
        TaxonomyCategoryMatchScheduler scheduler = scheduler(new TaxonomyCategoryMatchProperties(1, 300000));

        // when
        scheduler.sweep();

        // then
        ArgumentCaptor<CategoryMatchRequest> captor = ArgumentCaptor.forClass(CategoryMatchRequest.class);
        verify(pimCatalog).submitCategoryMatch(captor.capture());
        CategoryMatchRequest request = captor.getValue();
        assertThat(request.ean()).isEqualTo("1234567890123");
        assertThat(request.mfn()).isEqualTo("MFN-1");
        assertThat(request.brand()).isEqualTo("Brand");
        assertThat(request.name()).isEqualTo("Name");
        assertThat(request.supplier()).isNull();
        assertThat(request.rawCategory()).isNull();
    }

    @Test
    void sweepPassesRawCategoryIntoRequest() {
        // given
        cache.add(new Taxonomy("1234567890123", "MFN-1", "Brand", "Name", null, 5, null, null, "Karty graficzne"));
        TaxonomyCategoryMatchScheduler scheduler = scheduler(new TaxonomyCategoryMatchProperties(1, 300000));

        // when
        scheduler.sweep();

        // then
        ArgumentCaptor<CategoryMatchRequest> captor = ArgumentCaptor.forClass(CategoryMatchRequest.class);
        verify(pimCatalog).submitCategoryMatch(captor.capture());
        assertThat(captor.getValue().rawCategory()).isEqualTo("Karty graficzne");
    }

    @Test
    void sweepAbortsQuietlyWhenSqsIsNotConfigured() {
        // given
        cache.add(pending("MFN-1"));
        doThrow(new IllegalStateException("no sqs")).when(pimCatalog).submitCategoryMatch(any());
        TaxonomyCategoryMatchScheduler scheduler = scheduler(new TaxonomyCategoryMatchProperties(1, 300000));

        // when / then
        scheduler.sweep();
    }

    @Test
    void activeMappingResolvesPendingWithoutSubmit() {
        // given
        String mfn = mfnWhere(residue -> residue != 0);
        TaxonomyCategoryMatchProperties properties = new TaxonomyCategoryMatchProperties(1, 300000);
        TaxonomyCategoryEnrichment enrichment = enrichmentFor(properties);
        enrichment.addPending(new Taxonomy("1234567890123", mfn, "Brand", "Name", null, 5, null, null, "Karty graficzne"), "Acme");
        when(mappingCache.findActive("Acme", "Karty graficzne"))
                .thenReturn(Optional.of(new CategoryMappingCache.ActiveMapping("301", "GPU")));
        TaxonomyCategoryMatchScheduler scheduler = new TaxonomyCategoryMatchScheduler(
                cache, pimCatalog, properties, enrichment, mappingCache);

        // when
        scheduler.sweep();

        // then
        assertThat(cache.findByMfn(mfn).category()).isEqualTo("GPU");
        assertThat(cache.findByMfn(mfn).categoryId()).isEqualTo("301");
        verify(pimCatalog, never()).submitCategoryMatch(any());
    }

    @Test
    void mappingMissFallsBackToSubmit() {
        // given
        String mfn = mfnWhere(residue -> residue != 0);
        TaxonomyCategoryMatchProperties properties = new TaxonomyCategoryMatchProperties(1, 300000);
        TaxonomyCategoryEnrichment enrichment = enrichmentFor(properties);
        enrichment.addPending(new Taxonomy("1234567890123", mfn, "Brand", "Name", null, 5, null, null, "Karty graficzne"), "Acme");
        when(mappingCache.findActive("Acme", "Karty graficzne")).thenReturn(Optional.empty());
        TaxonomyCategoryMatchScheduler scheduler = new TaxonomyCategoryMatchScheduler(
                cache, pimCatalog, properties, enrichment, mappingCache);

        // when
        scheduler.sweep();

        // then
        verify(pimCatalog).submitCategoryMatch(any());
        assertThat(cache.findByMfn(mfn).category()).isNull();
    }

    @Test
    void trickleMfnBypassesMappingCache() {
        // given
        String mfn = mfnWhere(residue -> residue == 0);
        TaxonomyCategoryMatchProperties properties = new TaxonomyCategoryMatchProperties(1, 300000);
        TaxonomyCategoryEnrichment enrichment = enrichmentFor(properties);
        enrichment.addPending(new Taxonomy("1234567890123", mfn, "Brand", "Name", null, 5, null, null, "Karty graficzne"), "Acme");
        TaxonomyCategoryMatchScheduler scheduler = new TaxonomyCategoryMatchScheduler(
                cache, pimCatalog, properties, enrichment, mappingCache);

        // when
        scheduler.sweep();

        // then
        verify(mappingCache, never()).findActive(any(), any());
        verify(pimCatalog).submitCategoryMatch(any());
    }

    @Test
    void pendingWithoutSupplierSkipsMappingLookup() {
        // given
        String mfn = mfnWhere(residue -> residue != 0);
        TaxonomyCategoryMatchProperties properties = new TaxonomyCategoryMatchProperties(1, 300000);
        cache.add(new Taxonomy("1234567890123", mfn, "Brand", "Name", null, 5, null, null, "Karty graficzne"));
        TaxonomyCategoryMatchScheduler scheduler = new TaxonomyCategoryMatchScheduler(
                cache, pimCatalog, properties, enrichmentFor(properties), mappingCache);

        // when
        scheduler.sweep();

        // then
        verify(mappingCache, never()).findActive(any(), any());
        verify(pimCatalog).submitCategoryMatch(any());
    }

    private static Taxonomy pending(String mfn) {
        return new Taxonomy("1234567890123", mfn, "Brand", "Name", null, 5, null, null);
    }
}
