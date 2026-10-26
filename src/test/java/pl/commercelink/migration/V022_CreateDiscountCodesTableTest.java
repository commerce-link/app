package pl.commercelink.migration;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.model.BillingMode;
import com.amazonaws.services.dynamodbv2.model.CreateTableRequest;
import com.amazonaws.services.dynamodbv2.model.DescribeTableRequest;
import com.amazonaws.services.dynamodbv2.model.DescribeTableResult;
import com.amazonaws.services.dynamodbv2.model.KeySchemaElement;
import com.amazonaws.services.dynamodbv2.model.TableDescription;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class V022_CreateDiscountCodesTableTest {

    @Mock
    AmazonDynamoDB dynamoDB;

    @Test
    void createsTheTableKeyedByStoreAndCodeWithoutIndexes() {
        // given
        when(dynamoDB.describeTable(any(DescribeTableRequest.class)))
                .thenReturn(new DescribeTableResult().withTable(new TableDescription()
                        .withTableName("DiscountCodes").withTableStatus("ACTIVE")));

        // when
        new V022_CreateDiscountCodesTable(dynamoDB).createTable();

        // then
        ArgumentCaptor<CreateTableRequest> request = ArgumentCaptor.forClass(CreateTableRequest.class);
        verify(dynamoDB).createTable(request.capture());
        assertThat(request.getValue().getTableName()).isEqualTo("DiscountCodes");
        assertThat(request.getValue().getKeySchema()).extracting(KeySchemaElement::getAttributeName)
                .containsExactly("storeId", "code");
        assertThat(request.getValue().getGlobalSecondaryIndexes()).isNullOrEmpty();
        assertThat(request.getValue().getBillingMode()).isEqualTo(BillingMode.PAY_PER_REQUEST.toString());
    }
}
