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

import pl.commercelink.documents.DocumentType;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
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
    @DisplayName("search queries the CreatedAtIndex with type, warehouse and date criteria")
    void searchBuildsQueryFromCriteria() {
        // given
        ArgumentCaptor<DynamoDBQueryExpression<WarehouseDocument>> queryCaptor = ArgumentCaptor.forClass(DynamoDBQueryExpression.class);
        when(dynamoDBMapper.query(eq(WarehouseDocument.class), queryCaptor.capture())).thenReturn(paginatedQueryList);
        when(paginatedQueryList.iterator()).thenReturn(List.of(document("doc-1")).iterator());

        // when
        List<WarehouseDocument> result = warehouseDocumentRepository.search(
                "store-1", DocumentType.GoodsReceipt, LocalDateTime.of(2026, 8, 1, 0, 0),
                LocalDateTime.of(2026, 8, 13, 23, 59), "wh-1", 1, 26);

        // then
        assertThat(result).extracting(WarehouseDocument::getDocumentId).containsExactly("doc-1");
        DynamoDBQueryExpression<WarehouseDocument> queryExpression = queryCaptor.getValue();
        assertThat(queryExpression.getIndexName()).isEqualTo("CreatedAtIndex");
        assertThat(queryExpression.getKeyConditionExpression()).isEqualTo("storeId = :storeId AND createdAt BETWEEN :dateFrom AND :dateTo");
        assertThat(queryExpression.getFilterExpression()).isEqualTo("#type = :type and warehouseId = :warehouseId");
        assertThat(queryExpression.getExpressionAttributeValues())
                .containsKeys(":storeId", ":type", ":warehouseId", ":dateFrom", ":dateTo");
    }

    @Test
    @DisplayName("findAllMatching applies the same criteria without pagination")
    void findAllMatchingBuildsQueryFromCriteria() {
        // given
        ArgumentCaptor<DynamoDBQueryExpression<WarehouseDocument>> queryCaptor = ArgumentCaptor.forClass(DynamoDBQueryExpression.class);
        when(dynamoDBMapper.query(eq(WarehouseDocument.class), queryCaptor.capture())).thenReturn(paginatedQueryList);

        // when
        List<WarehouseDocument> result = warehouseDocumentRepository.findAllMatching(
                "store-1", DocumentType.GoodsReceipt, null, null, null);

        // then
        assertThat(result).isSameAs(paginatedQueryList);
        DynamoDBQueryExpression<WarehouseDocument> queryExpression = queryCaptor.getValue();
        assertThat(queryExpression.getKeyConditionExpression()).isEqualTo("storeId = :storeId");
        assertThat(queryExpression.getFilterExpression()).isEqualTo("#type = :type");
        assertThat(queryExpression.getExpressionAttributeNames()).containsEntry("#type", "type");
    }

    private WarehouseDocument document(String documentId) {
        WarehouseDocument document = new WarehouseDocument();
        document.setStoreId("store-1");
        document.setDocumentId(documentId);
        return document;
    }
}
