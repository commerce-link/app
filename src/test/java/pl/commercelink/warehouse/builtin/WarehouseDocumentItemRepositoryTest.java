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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WarehouseDocumentItemRepositoryTest {

    @Mock
    private AmazonDynamoDB amazonDynamoDB;
    @Mock
    private DynamoDBMapper dynamoDBMapper;
    @Mock
    private PaginatedQueryList<WarehouseDocumentItem> queryList;

    private WarehouseDocumentItemRepository warehouseDocumentItemRepository;

    @BeforeEach
    void setup() {
        warehouseDocumentItemRepository = new WarehouseDocumentItemRepository(amazonDynamoDB);
        ReflectionTestUtils.setField(warehouseDocumentItemRepository, "dynamoDBMapper", dynamoDBMapper);
    }

    @Test
    @DisplayName("documentContainsProduct queries by documentId and filters by both ean and mfn")
    void documentContainsProductFiltersByEanAndMfn() {
        // given
        ArgumentCaptor<DynamoDBQueryExpression<WarehouseDocumentItem>> queryCaptor = ArgumentCaptor.forClass(DynamoDBQueryExpression.class);
        when(dynamoDBMapper.query(eq(WarehouseDocumentItem.class), queryCaptor.capture())).thenReturn(queryList);
        when(queryList.isEmpty()).thenReturn(false);

        // when
        boolean result = warehouseDocumentItemRepository.documentContainsProduct("doc-1", "5901234123457", "MFN-123");

        // then
        assertThat(result).isTrue();
        DynamoDBQueryExpression<WarehouseDocumentItem> queryExpression = queryCaptor.getValue();
        assertThat(queryExpression.getKeyConditionExpression()).isEqualTo("documentId = :documentId");
        assertThat(queryExpression.getFilterExpression()).isEqualTo("ean = :ean and mfn = :mfn");
        assertThat(queryExpression.getExpressionAttributeValues().get(":documentId").getS()).isEqualTo("doc-1");
        assertThat(queryExpression.getExpressionAttributeValues().get(":ean").getS()).isEqualTo("5901234123457");
        assertThat(queryExpression.getExpressionAttributeValues().get(":mfn").getS()).isEqualTo("MFN-123");
    }

    @Test
    @DisplayName("documentContainsProduct filters only by ean and returns false when nothing matches")
    void documentContainsProductFiltersByEanOnly() {
        // given
        ArgumentCaptor<DynamoDBQueryExpression<WarehouseDocumentItem>> queryCaptor = ArgumentCaptor.forClass(DynamoDBQueryExpression.class);
        when(dynamoDBMapper.query(eq(WarehouseDocumentItem.class), queryCaptor.capture())).thenReturn(queryList);
        when(queryList.isEmpty()).thenReturn(true);

        // when
        boolean result = warehouseDocumentItemRepository.documentContainsProduct("doc-1", "5901234123457", null);

        // then
        assertThat(result).isFalse();
        DynamoDBQueryExpression<WarehouseDocumentItem> queryExpression = queryCaptor.getValue();
        assertThat(queryExpression.getFilterExpression()).isEqualTo("ean = :ean");
        assertThat(queryExpression.getExpressionAttributeValues()).containsOnlyKeys(":documentId", ":ean");
    }
}
