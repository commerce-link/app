package pl.commercelink.warehouse.builtin;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import pl.commercelink.taxonomy.UnifiedProductIdentifiers;

import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WarehouseDocumentSearchServiceTest {

    @Mock
    private WarehouseDocumentRepository warehouseDocumentRepository;
    @Mock
    private WarehouseDocumentItemRepository warehouseDocumentItemRepository;

    @InjectMocks
    private WarehouseDocumentSearchService warehouseDocumentSearchService;

    private static final WarehouseDocumentCriteria CRITERIA =
            new WarehouseDocumentCriteria("store-1", null, Set.of(), null, null, List.of());

    @Test
    @DisplayName("without a product code the service passes the page size through, the starter adds the extra row")
    void searchDelegatesToRepositoryWithoutProductCode() {
        // given
        List<WarehouseDocument> documents = List.of(document("doc-1"));
        when(warehouseDocumentRepository.search(CRITERIA, 2, 25)).thenReturn(documents);

        // when
        List<WarehouseDocument> result = warehouseDocumentSearchService.search(CRITERIA, null, 2, 25);

        // then
        assertThat(result).isEqualTo(documents);
        verify(warehouseDocumentRepository, never()).findAllMatching(any());
    }

    @Test
    @DisplayName("a product code keeps documents with an item of that EAN or MFN, normalised like stored items")
    void searchFiltersDocumentsByProductCode() {
        // given
        when(warehouseDocumentRepository.findAllMatching(CRITERIA))
                .thenReturn(List.of(document("doc-1"), document("doc-2"), document("doc-3")));
        when(warehouseDocumentItemRepository.containsProduct(eq("doc-1"), any(), any())).thenReturn(true);
        when(warehouseDocumentItemRepository.containsProduct(eq("doc-2"), any(), any())).thenReturn(false);
        when(warehouseDocumentItemRepository.containsProduct(eq("doc-3"), any(), any())).thenReturn(true);

        // when
        List<WarehouseDocument> result = warehouseDocumentSearchService.search(CRITERIA, " mz-v9p2t0bw ", 1, 25);

        // then
        assertThat(result).extracting(WarehouseDocument::getDocumentId).containsExactly("doc-1", "doc-3");
        verify(warehouseDocumentItemRepository).containsProduct("doc-1",
                UnifiedProductIdentifiers.unifyEan("mz-v9p2t0bw"), UnifiedProductIdentifiers.unifyMfn("mz-v9p2t0bw"));
    }

    @Test
    @DisplayName("page 2 of a product search starts right after the 25 matches of page 1")
    void secondPageStartsRightAfterTheFirst() {
        // given
        List<WarehouseDocument> all = IntStream.rangeClosed(1, 27).mapToObj(i -> document("doc-" + i)).toList();
        when(warehouseDocumentRepository.findAllMatching(CRITERIA)).thenReturn(all);
        when(warehouseDocumentItemRepository.containsProduct(any(), any(), any())).thenReturn(true);

        // when
        List<WarehouseDocument> page2 = warehouseDocumentSearchService.search(CRITERIA, "5901234123457", 2, 25);

        // then
        assertThat(page2).extracting(WarehouseDocument::getDocumentId).containsExactly("doc-26", "doc-27");
    }

    @Test
    @DisplayName("a product search stops reading after the page plus one match")
    void productSearchStopsAfterPagePlusOne() {
        // given
        List<WarehouseDocument> all = IntStream.rangeClosed(1, 100).mapToObj(i -> document("doc-" + i)).toList();
        when(warehouseDocumentRepository.findAllMatching(CRITERIA)).thenReturn(all);
        when(warehouseDocumentItemRepository.containsProduct(any(), any(), any())).thenReturn(true);

        // when
        List<WarehouseDocument> page1 = warehouseDocumentSearchService.search(CRITERIA, "5901234123457", 1, 25);

        // then
        assertThat(page1).hasSize(26);
        verify(warehouseDocumentItemRepository, times(26)).containsProduct(any(), any(), any());
    }

    private WarehouseDocument document(String documentId) {
        WarehouseDocument document = new WarehouseDocument();
        document.setStoreId("store-1");
        document.setDocumentId(documentId);
        return document;
    }
}
