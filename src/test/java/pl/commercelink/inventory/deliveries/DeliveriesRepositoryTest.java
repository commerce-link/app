package pl.commercelink.inventory.deliveries;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.PaginatedQueryList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeliveriesRepositoryTest {

    @Mock
    private AmazonDynamoDB amazonDynamoDB;
    @Mock
    private DynamoDBMapper dynamoDBMapper;
    @Mock
    private PaginatedQueryList<Delivery> paginatedQueryList;

    private DeliveriesRepository deliveriesRepository;

    @BeforeEach
    void setup() {
        deliveriesRepository = new DeliveriesRepository(amazonDynamoDB);
        ReflectionTestUtils.setField(deliveriesRepository, "dynamoDBMapper", dynamoDBMapper);
    }

    @Test
    @DisplayName("searchActiveDeliveries does not throw when some deliveries have a null estimatedDeliveryAt and sorts them first")
    void searchActiveDeliveriesDoesNotThrowOnNullEstimatedDeliveryAtAndSortsThemFirst() {
        // given
        Delivery pending = delivery("d-pending", null);
        Delivery earlier = delivery("d-earlier", LocalDate.of(2026, 8, 12));
        Delivery later = delivery("d-later", LocalDate.of(2026, 8, 20));
        List<Delivery> deliveries = Arrays.asList(later, pending, earlier);
        when(dynamoDBMapper.query(eq(Delivery.class), any(DynamoDBQueryExpression.class))).thenReturn(paginatedQueryList);
        when(paginatedQueryList.iterator()).thenReturn(deliveries.iterator());
        DeliveryFilter filter = new DeliveryFilter(null, null, null, null, null, true, false, false);

        // when
        List<Delivery> result = deliveriesRepository.searchActiveDeliveries("store-1", filter, 1, 25);

        // then
        assertThat(result).extracting(Delivery::getDeliveryId)
                .containsExactly("d-pending", "d-earlier", "d-later");
    }

    private Delivery delivery(String deliveryId, LocalDate estimatedDeliveryAt) {
        Delivery delivery = new Delivery();
        delivery.setStoreId("store-1");
        delivery.setDeliveryId(deliveryId);
        delivery.setEstimatedDeliveryAt(estimatedDeliveryAt);
        return delivery;
    }
}
