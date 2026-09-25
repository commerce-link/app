package pl.commercelink.web.inventory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.inventory.Inventory;
import pl.commercelink.pim.api.PimCatalog;
import pl.commercelink.taxonomy.TaxonomyCatalog;
import pl.commercelink.taxonomy.TaxonomyRepository;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TechnicalInventoryViewFactoryTest {

    @Mock
    private Inventory inventory;

    @Mock
    private TaxonomyCatalog taxonomyCatalog;

    @Mock
    private TaxonomyRepository taxonomyRepository;

    @Mock
    private PimCatalog pimCatalog;

    @InjectMocks
    private TechnicalInventoryViewFactory factory;

    @Test
    void collectsPlatformSizesAndGlobalFeedLoadTimesSortedBySupplier() {
        // given
        LocalDateTime now = LocalDateTime.of(2026, 9, 14, 12, 0);
        when(inventory.size()).thenReturn(120_000);
        when(inventory.getMatchedSuppliers()).thenReturn(List.of("elko", "AB"));
        when(inventory.getLastUpdateDate("AB")).thenReturn(now.minusHours(2));
        when(inventory.getLastUpdateDate("elko")).thenReturn(now.minusMinutes(5));
        when(taxonomyCatalog.approximateSize()).thenReturn(80_000L);
        when(taxonomyRepository.newestFileName()).thenReturn("2026-09-21.csv");
        when(pimCatalog.findAll()).thenReturn(List.of());

        // when
        TechnicalInventoryView view = factory.build(now);

        // then
        assertThat(view.globalInventorySize()).isEqualTo(120_000);
        assertThat(view.taxonomySize()).isEqualTo(80_000L);
        assertThat(view.taxonomyFileName()).isEqualTo("2026-09-21.csv");
        assertThat(view.pimIndexSize()).isZero();
        assertThat(view.feeds()).extracting(GlobalFeedRow::supplier).containsExactly("AB", "elko");
        assertThat(view.feeds().get(1).age()).isEqualTo(new RelativeTime("inventory.time.minutes", 5));
    }
}
