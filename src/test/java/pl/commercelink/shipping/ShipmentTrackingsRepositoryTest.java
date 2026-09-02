package pl.commercelink.shipping;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBSaveExpression;
import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShipmentTrackingsRepositoryTest {

    @Mock
    private AmazonDynamoDB amazonDynamoDB;
    @Mock
    private DynamoDBMapper dynamoDBMapper;

    private ShipmentTrackingsRepository repository;

    @BeforeEach
    void setup() {
        repository = new ShipmentTrackingsRepository(amazonDynamoDB);
        ReflectionTestUtils.setField(repository, "dynamoDBMapper", dynamoDBMapper);
    }

    @Test
    void saveIfAbsentUsesConditionalPutOnTrackingNo() {
        // given
        ShipmentTracking tracking = new ShipmentTracking("store-1", "PKG-1", "order-1", null, LocalDateTime.now());
        ArgumentCaptor<DynamoDBSaveExpression> expression = ArgumentCaptor.forClass(DynamoDBSaveExpression.class);

        // when
        boolean saved = repository.saveIfAbsent(tracking);

        // then
        assertThat(saved).isTrue();
        verify(dynamoDBMapper).save(eq(tracking), expression.capture());
        assertThat(expression.getValue().getExpected()).containsKey("trackingNo");
        assertThat(expression.getValue().getExpected().get("trackingNo").getExists()).isFalse();
    }

    @Test
    void saveIfAbsentReturnsFalseWhenEntryExists() {
        // given
        ShipmentTracking tracking = new ShipmentTracking("store-1", "PKG-1", "order-1", null, LocalDateTime.now());
        doThrow(new ConditionalCheckFailedException("exists")).when(dynamoDBMapper).save(eq(tracking), any(DynamoDBSaveExpression.class));

        // when
        boolean saved = repository.saveIfAbsent(tracking);

        // then
        assertThat(saved).isFalse();
    }

    @Test
    void findLoadsByCompositeKey() {
        // given
        ShipmentTracking tracking = new ShipmentTracking("store-1", "PKG-1", "order-1", null, LocalDateTime.now());
        when(dynamoDBMapper.load(ShipmentTracking.class, "store-1", "PKG-1")).thenReturn(tracking);

        // when
        Optional<ShipmentTracking> found = repository.find("store-1", "PKG-1");
        Optional<ShipmentTracking> missing = repository.find("store-1", "PKG-2");

        // then
        assertThat(found).contains(tracking);
        assertThat(missing).isEmpty();
    }
}
