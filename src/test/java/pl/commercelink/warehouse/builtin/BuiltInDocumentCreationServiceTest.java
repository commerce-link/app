package pl.commercelink.warehouse.builtin;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.documents.DocumentType;
import pl.commercelink.taxonomy.Taxonomy;
import pl.commercelink.taxonomy.TaxonomyCache;
import pl.commercelink.taxonomy.TaxonomyRepository;
import pl.commercelink.taxonomy.TaxonomyResolver;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BuiltInDocumentCreationServiceTest {

    private static final String CODE = "MZ-V9P2T0BW";
    private static final String EAN_PLAIN = "8806094215038";
    private static final String EAN_HEATSINK = "8806094215045";

    @Mock
    private WarehouseDocumentRepository documents;
    @Mock
    private WarehouseDocumentItemRepository documentItems;
    @Captor
    private ArgumentCaptor<List<WarehouseDocumentItem>> savedLines;

    private TaxonomyCache taxonomy;
    private BuiltInDocumentCreationService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        taxonomy = new TaxonomyCache(mock(TaxonomyRepository.class));
        service = new BuiltInDocumentCreationService(documents, documentItems, new TaxonomyResolver(taxonomy));
        when(documents.saveWithSequence(anyString(), anyString(), anyInt(), any()))
                .thenAnswer(call -> Optional.of(((Function<String, WarehouseDocument>) call.getArgument(3)).apply("PZ/000001")));
    }

    private String nameOfTheSavedLine(String ean) {
        DocumentCreationRequest request = DocumentCreationRequest.builder(DocumentType.GoodsReceipt)
                .storeId("store-1")
                .warehouseId("main")
                .items(List.of(new DocumentLineItem("d-1", ean, CODE, "Samsung 990 Pro z faktury", 1, 689.0)))
                .build();
        service.createDocument(request);
        verify(documentItems).saveAll(savedLines.capture());
        return savedLines.getValue().get(0).getName();
    }

    @Test
    void documentLineTakesTheRecordOfItsEan() {
        // given
        taxonomy.add(new Taxonomy(EAN_PLAIN, CODE, "Samsung", "Samsung 990 Pro 2TB", "Dyski SSD", 1, null, null));
        taxonomy.add(new Taxonomy(EAN_HEATSINK, CODE, "Samsung", "Samsung 990 Pro 2TB z radiatorem", "Dyski SSD", 5, null, null));

        // when
        String name = nameOfTheSavedLine(EAN_HEATSINK);

        // then
        assertThat(name).isEqualTo("Samsung 990 Pro 2TB z radiatorem");
    }

    @Test
    void documentLineWithoutRecordKeepsItsOwnName() {
        // given
        taxonomy.add(new Taxonomy(EAN_PLAIN, CODE, "Samsung", "Samsung 990 Pro 2TB", "Dyski SSD", 1, null, null));

        // when
        String name = nameOfTheSavedLine(EAN_HEATSINK);

        // then
        assertThat(name).isEqualTo("Samsung 990 Pro z faktury");
    }
}
