package pl.commercelink.orders;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.model.AmazonDynamoDBException;
import com.amazonaws.services.dynamodbv2.model.QueryRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** Right after V023 DynamoDB is still backfilling DiscountCodeIndex: the details page shows the code without its orders. */
@ExtendWith(MockitoExtension.class)
class OrdersByDiscountCodeIndexUnavailableTest {

    @Mock
    AmazonDynamoDB dynamoDB;

    @Test
    void anIndexStillBeingBuiltGivesNoOrders() {
        // given
        when(dynamoDB.query(any(QueryRequest.class))).thenThrow(
                new AmazonDynamoDBException("Cannot read from backfilling global secondary index: DiscountCodeIndex"));

        // when / then
        assertThat(new OrdersRepository(dynamoDB).findAllByDiscountCode("store-a", "LATO-10", null, null)).isEmpty();
    }

    @Test
    void anyOtherFailureIsNotHidden() {
        // given
        when(dynamoDB.query(any(QueryRequest.class))).thenThrow(new AmazonDynamoDBException("Rate of requests exceeds the allowed throughput"));

        // when / then
        assertThatThrownBy(() -> new OrdersRepository(dynamoDB).findAllByDiscountCode("store-a", "LATO-10", null, null))
                .isInstanceOf(AmazonDynamoDBException.class);
    }
}
