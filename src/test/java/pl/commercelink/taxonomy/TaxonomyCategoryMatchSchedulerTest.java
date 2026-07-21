package pl.commercelink.taxonomy;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.inventory.supplier.api.Taxonomy;
import pl.commercelink.pim.api.CategoryMatchRequest;
import pl.commercelink.pim.api.PimCatalog;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TaxonomyCategoryMatchSchedulerTest {

    private TaxonomyCache cache;

    @Mock
    private TaxonomyRepository taxonomyRepository;

    @Mock
    private PimCatalog pimCatalog;

    @BeforeEach
    void setUp() {
        Mockito.when(taxonomyRepository.loadNewest()).thenReturn(Pair.of("N/A", new ArrayList<>()));
        cache = new TaxonomyCache(taxonomyRepository);
        cache.onStartUp();
    }

    @Test
    void sweepIsNoopWhenNoSuppliersConfigured() {
        // given
        cache.add(pending("MFN-1"));
        TaxonomyCategoryMatchScheduler scheduler = new TaxonomyCategoryMatchScheduler(
                cache, pimCatalog, new TaxonomyCategoryMatchProperties("", 4, 300000));

        // when
        scheduler.sweep();

        // then
        verify(pimCatalog, never()).submitCategoryMatch(any());
    }

    @Test
    void everyPendingRowIsSubmittedExactlyOnceAcrossFullBucketCycle() {
        // given
        List<String> pendingMfns = IntStream.range(0, 20).mapToObj(i -> "MFN-P-" + i).toList();
        pendingMfns.forEach(mfn -> cache.add(pending(mfn)));
        cache.add(new Taxonomy("1234567890123", "MFN-CAT", "Brand", "Name", "CPU", 5, null, null));
        TaxonomyCategoryMatchScheduler scheduler = new TaxonomyCategoryMatchScheduler(
                cache, pimCatalog, new TaxonomyCategoryMatchProperties("Acme", 4, 300000));

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
        cache.add(new Taxonomy("1234567890123", "MFN-1", "Brand", "Name", "Other", 5, null, null));
        TaxonomyCategoryMatchScheduler scheduler = new TaxonomyCategoryMatchScheduler(
                cache, pimCatalog, new TaxonomyCategoryMatchProperties("Acme", 1, 300000));

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
    void sweepAbortsQuietlyWhenSqsIsNotConfigured() {
        // given
        cache.add(pending("MFN-1"));
        doThrow(new IllegalStateException("no sqs")).when(pimCatalog).submitCategoryMatch(any());
        TaxonomyCategoryMatchScheduler scheduler = new TaxonomyCategoryMatchScheduler(
                cache, pimCatalog, new TaxonomyCategoryMatchProperties("Acme", 1, 300000));

        // when / then
        scheduler.sweep();
    }

    private static Taxonomy pending(String mfn) {
        return new Taxonomy("1234567890123", mfn, "Brand", "Name", "Other", 5, null, null);
    }
}
