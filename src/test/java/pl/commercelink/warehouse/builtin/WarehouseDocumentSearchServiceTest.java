package pl.commercelink.warehouse.builtin;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
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

    @Test
    @DisplayName("search delegates to the paginated repository search when no product code is given")
    void searchDelegatesToRepositoryWithoutProductCode() {
        // given
        List<WarehouseDocument> documents = List.of(document("doc-1"));
        when(warehouseDocumentRepository.search("store-1", null, null, null, null, 1, 26)).thenReturn(documents);

        // when
        List<WarehouseDocument> result = warehouseDocumentSearchService.search(
                "store-1", null, null, null, null, null, " ", 1, 26);

        // then
        assertThat(result).isEqualTo(documents);
        verify(warehouseDocumentRepository, never()).findAllMatching(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("search keeps only documents containing an item with the given product code")
    void searchFiltersDocumentsByProductCode() {
        // given
        when(warehouseDocumentRepository.findAllMatching("store-1", null, null, null, null))
                .thenReturn(List.of(document("doc-1"), document("doc-2"), document("doc-3")));
        when(warehouseDocumentItemRepository.documentContainsProduct("doc-1", "5901234123457", null)).thenReturn(true);
        when(warehouseDocumentItemRepository.documentContainsProduct("doc-2", "5901234123457", null)).thenReturn(false);
        when(warehouseDocumentItemRepository.documentContainsProduct("doc-3", "5901234123457", null)).thenReturn(true);

        // when
        List<WarehouseDocument> result = warehouseDocumentSearchService.search(
                "store-1", null, null, null, null, "5901234123457", null, 1, 26);

        // then
        assertThat(result).extracting(WarehouseDocument::getDocumentId).containsExactly("doc-1", "doc-3");
    }

    @Test
    @DisplayName("search paginates the filtered documents")
    void searchPaginatesFilteredDocuments() {
        // given
        when(warehouseDocumentRepository.findAllMatching("store-1", null, null, null, null))
                .thenReturn(List.of(document("doc-1"), document("doc-2"), document("doc-3"), document("doc-4"), document("doc-5")));
        when(warehouseDocumentItemRepository.documentContainsProduct(any(), eq("MFN-123"), any())).thenReturn(true);
        when(warehouseDocumentItemRepository.documentContainsProduct("doc-2", "MFN-123", null)).thenReturn(false);

        // when
        List<WarehouseDocument> result = warehouseDocumentSearchService.search(
                "store-1", null, null, null, null, "MFN-123", null, 2, 2);

        // then
        assertThat(result).extracting(WarehouseDocument::getDocumentId).containsExactly("doc-4", "doc-5");
    }

    private WarehouseDocument document(String documentId) {
        WarehouseDocument document = new WarehouseDocument();
        document.setStoreId("store-1");
        document.setDocumentId(documentId);
        return document;
    }
}
