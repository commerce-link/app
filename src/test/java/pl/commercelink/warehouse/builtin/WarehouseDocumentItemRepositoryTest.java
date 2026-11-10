package pl.commercelink.warehouse.builtin;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.PaginatedQueryList;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
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
    @DisplayName("containsProduct matches an item by its EAN alone, never by manufacturer code")
    void productSearchMatchesOnlyTheEan() {
        // given
        ArgumentCaptor<DynamoDBQueryExpression<WarehouseDocumentItem>> queryCaptor = ArgumentCaptor.forClass(DynamoDBQueryExpression.class);
        when(dynamoDBMapper.query(eq(WarehouseDocumentItem.class), queryCaptor.capture())).thenReturn(queryList);
        when(queryList.isEmpty()).thenReturn(false);

        // when
        boolean result = warehouseDocumentItemRepository.containsProduct("doc-1", "5901234123457");

        // then
        assertThat(result).isTrue();
        assertThat(queryCaptor.getValue().getKeyConditionExpression()).isEqualTo("documentId = :documentId");
        assertThat(queryCaptor.getValue().getFilterExpression()).isEqualTo("ean = :ean");
        assertThat(queryCaptor.getValue().getExpressionAttributeValues())
                .containsOnlyKeys(":documentId", ":ean")
                .containsEntry(":ean", new AttributeValue().withS("5901234123457"));
    }
}
