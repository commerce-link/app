package pl.commercelink.migration;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.model.CreateTableRequest;
import com.amazonaws.services.dynamodbv2.model.DescribeTableRequest;
import com.amazonaws.services.dynamodbv2.model.DescribeTableResult;
import com.amazonaws.services.dynamodbv2.model.TableDescription;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import pl.commercelink.marketplace.links.MarketplaceOfferLink;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class V024_CreateMarketplaceOfferLinksTableTest {

    @Test
    void createsTheTableWithThePimIdIndex() {
        // given
        AmazonDynamoDB dynamoDB = mock(AmazonDynamoDB.class);
        when(dynamoDB.describeTable(any(DescribeTableRequest.class)))
                .thenReturn(new DescribeTableResult().withTable(new TableDescription()
                        .withTableName(MarketplaceOfferLink.TABLE_NAME).withTableStatus("ACTIVE")));

        // when
        new V024_CreateMarketplaceOfferLinksTable(dynamoDB).createTable();

        // then
        ArgumentCaptor<CreateTableRequest> request = ArgumentCaptor.forClass(CreateTableRequest.class);
        verify(dynamoDB).createTable(request.capture());
        assertThat(request.getValue().getTableName()).isEqualTo("MarketplaceOfferLinks");
        assertThat(request.getValue().getKeySchema()).extracting(k -> k.getAttributeName())
                .containsExactly("storeId", "linkKey");
        assertThat(request.getValue().getGlobalSecondaryIndexes()).singleElement().satisfies(index -> {
            assertThat(index.getIndexName()).isEqualTo("StoreIdPimIdIndex");
            assertThat(index.getKeySchema()).extracting(k -> k.getAttributeName())
                    .containsExactly("storeId", "pimId");
        });
    }
}
