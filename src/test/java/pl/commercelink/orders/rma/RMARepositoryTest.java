package pl.commercelink.orders.rma;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBScanExpression;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RMARepositoryTest {

    @Mock
    private AmazonDynamoDB amazonDynamoDB;
    @Mock
    private DynamoDBMapper dynamoDBMapper;

    private RMARepository repository;

    @BeforeEach
    void setUp() {
        repository = new RMARepository(amazonDynamoDB);
        ReflectionTestUtils.setField(repository, "dynamoDBMapper", dynamoDBMapper);
    }

    @Test
    @SuppressWarnings("unchecked")
    void theStoresRmasAreAQueryOfItsPartitionWithoutAStatusFilter() {
        // given
        ArgumentCaptor<DynamoDBQueryExpression<RMA>> query = ArgumentCaptor.forClass(DynamoDBQueryExpression.class);

        // when
        repository.findAllByStoreId("store-1");

        // then
        verify(dynamoDBMapper).query(eq(RMA.class), query.capture());
        assertThat(query.getValue().getHashKeyValues().getStoreId()).isEqualTo("store-1");
        assertThat(query.getValue().getFilterExpression()).isNull();
        verify(dynamoDBMapper, never()).scan(eq(RMA.class), any(DynamoDBScanExpression.class));
    }
}
