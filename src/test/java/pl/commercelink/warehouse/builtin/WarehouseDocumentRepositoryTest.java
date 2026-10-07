package pl.commercelink.warehouse.builtin;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.PaginatedQueryList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import pl.commercelink.documents.DocumentReason;
import pl.commercelink.documents.DocumentType;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WarehouseDocumentRepositoryTest {

    @Mock
    private AmazonDynamoDB amazonDynamoDB;
    @Mock
    private DynamoDBMapper dynamoDBMapper;
    @Mock
    private PaginatedQueryList<WarehouseDocument> paginatedQueryList;

    private WarehouseDocumentRepository warehouseDocumentRepository;

    @BeforeEach
    void setup() {
        warehouseDocumentRepository = new WarehouseDocumentRepository(amazonDynamoDB);
        ReflectionTestUtils.setField(warehouseDocumentRepository, "dynamoDBMapper", dynamoDBMapper);
    }

    @Test
    @DisplayName("search turns the criteria into a CreatedAtIndex query: date range, type, reasons and number")
    void searchBuildsQueryFromCriteria() {
        // given
        ArgumentCaptor<DynamoDBQueryExpression<WarehouseDocument>> queryCaptor = ArgumentCaptor.forClass(DynamoDBQueryExpression.class);
        when(dynamoDBMapper.query(eq(WarehouseDocument.class), queryCaptor.capture())).thenReturn(paginatedQueryList);
        when(paginatedQueryList.iterator()).thenReturn(List.of(document("doc-1")).iterator());
        WarehouseDocumentCriteria criteria = new WarehouseDocumentCriteria("store-1", DocumentType.GoodsReceipt,
                Set.of(DocumentReason.SupplierDelivery), LocalDateTime.of(2026, 8, 1, 0, 0),
                LocalDateTime.of(2026, 8, 13, 23, 59), "PZ/MAG1");

        // when
        List<WarehouseDocument> result = warehouseDocumentRepository.search(criteria, 1, 25);

        // then
        assertThat(result).extracting(WarehouseDocument::getDocumentId).containsExactly("doc-1");
        DynamoDBQueryExpression<WarehouseDocument> query = queryCaptor.getValue();
        assertThat(query.getIndexName()).isEqualTo("CreatedAtIndex");
        assertThat(query.isScanIndexForward()).isFalse();
        assertThat(query.getKeyConditionExpression()).isEqualTo("storeId = :storeId AND createdAt BETWEEN :dateFrom AND :dateTo");
        assertThat(query.getFilterExpression()).isEqualTo("#type = :type and #reason IN (:reason0) and contains(documentNo, :number)");
        assertThat(query.getExpressionAttributeNames()).containsEntry("#type", "type").containsEntry("#reason", "reason");
        assertThat(query.getExpressionAttributeValues().get(":number").getS()).isEqualTo("PZ/MAG1");
        assertThat(query.getExpressionAttributeValues().get(":reason0").getS()).isEqualTo("SupplierDelivery");
    }

    @Test
    @DisplayName("a date from alone narrows the key range from that day on")
    void dateFromAloneIsALowerBound() {
        // given
        ArgumentCaptor<DynamoDBQueryExpression<WarehouseDocument>> queryCaptor = ArgumentCaptor.forClass(DynamoDBQueryExpression.class);
        when(dynamoDBMapper.query(eq(WarehouseDocument.class), queryCaptor.capture())).thenReturn(paginatedQueryList);

        // when
        warehouseDocumentRepository.findAllMatching(new WarehouseDocumentCriteria("store-1", null, Set.of(),
                LocalDateTime.of(2026, 10, 1, 0, 0), null, null));

        // then
        assertThat(queryCaptor.getValue().getKeyConditionExpression()).isEqualTo("storeId = :storeId AND createdAt >= :dateFrom");
        assertThat(queryCaptor.getValue().getFilterExpression()).isNull();
        assertThat(queryCaptor.getValue().getExpressionAttributeNames()).isNull();
    }

    @Test
    @DisplayName("a date to alone narrows the key range up to that moment")
    void dateToAloneIsAnUpperBound() {
        // given
        ArgumentCaptor<DynamoDBQueryExpression<WarehouseDocument>> queryCaptor = ArgumentCaptor.forClass(DynamoDBQueryExpression.class);
        when(dynamoDBMapper.query(eq(WarehouseDocument.class), queryCaptor.capture())).thenReturn(paginatedQueryList);

        // when
        warehouseDocumentRepository.findAllMatching(new WarehouseDocumentCriteria("store-1", null, Set.of(),
                null, LocalDateTime.of(2026, 10, 7, 23, 59), null));

        // then
        assertThat(queryCaptor.getValue().getKeyConditionExpression()).isEqualTo("storeId = :storeId AND createdAt <= :dateTo");
    }

    @Test
    @DisplayName("several reasons become one IN filter with a value each")
    void severalReasonsBecomeOneInFilter() {
        // given
        ArgumentCaptor<DynamoDBQueryExpression<WarehouseDocument>> queryCaptor = ArgumentCaptor.forClass(DynamoDBQueryExpression.class);
        when(dynamoDBMapper.query(eq(WarehouseDocument.class), queryCaptor.capture())).thenReturn(paginatedQueryList);
        Set<DocumentReason> reasons = new LinkedHashSet<>(List.of(DocumentReason.Destruction, DocumentReason.Theft));

        // when
        warehouseDocumentRepository.findAllMatching(new WarehouseDocumentCriteria("store-1", null, reasons, null, null, null));

        // then
        assertThat(queryCaptor.getValue().getFilterExpression()).isEqualTo("#reason IN (:reason0, :reason1)");
        assertThat(queryCaptor.getValue().getExpressionAttributeValues()).containsKeys(":reason0", ":reason1");
    }

    @Test
    @DisplayName("page 2 of the index search starts at document 26 when asked with the page size of 25")
    void indexSearchPageTwoStartsAtTwentySix() {
        // given
        List<WarehouseDocument> all = IntStream.rangeClosed(1, 60).mapToObj(i -> document("doc-" + i)).toList();
        when(dynamoDBMapper.query(eq(WarehouseDocument.class), any(DynamoDBQueryExpression.class))).thenReturn(paginatedQueryList);
        when(paginatedQueryList.iterator()).thenReturn(all.iterator());

        // when
        List<WarehouseDocument> page2 = warehouseDocumentRepository.search(
                new WarehouseDocumentCriteria("store-1", null, Set.of(), null, null, null), 2, 25);

        // then
        assertThat(page2).hasSize(26);
        assertThat(page2.get(0).getDocumentId()).isEqualTo("doc-26");
    }

    private WarehouseDocument document(String documentId) {
        WarehouseDocument document = new WarehouseDocument();
        document.setStoreId("store-1");
        document.setDocumentId(documentId);
        return document;
    }
}
