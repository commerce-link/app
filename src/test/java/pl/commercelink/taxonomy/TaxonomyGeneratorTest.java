package pl.commercelink.taxonomy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.inventory.supplier.api.Taxonomy;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaxonomyGeneratorTest {

    @Mock
    private TaxonomyCache taxonomyCache;
    @Mock
    private TaxonomyRepository taxonomyRepository;
    @InjectMocks
    private TaxonomyGenerator generator;

    @Test
    void savesOnlyCategorizedTaxonomies() {
        // given
        Taxonomy categorized = new Taxonomy("1234567890123", "MFN-1", "Brand", "Name", "CPU", 5, null, null);
        Taxonomy pending = new Taxonomy("1234567890124", "MFN-2", "Brand", "Name", "Other", 5, null, null);
        when(taxonomyCache.getTaxonomies()).thenReturn(List.of(categorized, pending));

        // when
        generator.handleMessage("generate");

        // then
        verify(taxonomyRepository).save(List.of(categorized));
    }
}
