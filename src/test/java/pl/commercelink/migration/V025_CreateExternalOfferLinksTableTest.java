package pl.commercelink.migration;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.model.CreateTableRequest;
import com.amazonaws.services.dynamodbv2.model.DescribeTableRequest;
import com.amazonaws.services.dynamodbv2.model.DescribeTableResult;
import com.amazonaws.services.dynamodbv2.model.TableDescription;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import pl.commercelink.externaloffers.ExternalOfferLink;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class V025_CreateExternalOfferLinksTableTest {

    @Test
    void createsTheTableWithoutSecondaryIndexes() {
        // given
        AmazonDynamoDB dynamoDB = mock(AmazonDynamoDB.class);
        when(dynamoDB.describeTable(any(DescribeTableRequest.class)))
                .thenReturn(new DescribeTableResult().withTable(new TableDescription()
                        .withTableName(ExternalOfferLink.TABLE_NAME).withTableStatus("ACTIVE")));

        // when
        new V025_CreateExternalOfferLinksTable(dynamoDB).createTable();

        // then
        ArgumentCaptor<CreateTableRequest> request = ArgumentCaptor.forClass(CreateTableRequest.class);
        verify(dynamoDB).createTable(request.capture());
        assertThat(request.getValue().getTableName()).isEqualTo("ExternalOfferLinks");
        assertThat(request.getValue().getKeySchema()).extracting(k -> k.getAttributeName())
                .containsExactly("storeId", "linkKey");
        assertThat(request.getValue().getGlobalSecondaryIndexes()).isNullOrEmpty();
        assertThat(request.getValue().getAttributeDefinitions()).extracting(a -> a.getAttributeName())
                .containsExactlyInAnyOrder("storeId", "linkKey");
    }
}
